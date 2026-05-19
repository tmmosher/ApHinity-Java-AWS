package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocationDashboardSampleImportPipeline {
    private final LocationDashboardImportContextResolver contextResolver;
    private final LocationDashboardCommentParser commentParser;
    private final boolean enableCommentDerivedCorrectiveActions;

    LocationDashboardSampleImportPipeline(
        LocationDashboardImportContextResolver contextResolver,
        LocationDashboardCommentParser commentParser,
        boolean enableCommentDerivedCorrectiveActions
    ) {
        this.contextResolver = contextResolver;
        this.commentParser = commentParser;
        this.enableCommentDerivedCorrectiveActions = enableCommentDerivedCorrectiveActions;
    }

    SampleImportResult importSamples(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        Map<String, MeasurementBound> measurementBoundsByName
    ) {
        LocationDashboardSampleBuckets sampleBuckets = new LocationDashboardSampleBuckets();
        LocationDashboardImportContextResolver.ActiveImportContext activeContext = contextResolver.emptyContext();

        for (LocationDashboardSpreadsheetParser.ParsedDashboardRow row : workbook.rows()) {
            LocationDashboardImportContextResolver.RowImportContext rowContext =
                contextResolver.resolveRowContext(row, activeContext);

            for (LocationDashboardSpreadsheetParser.ParsedDashboardCell cell : row.cells()) {
                MeasurementBound measurementBound = measurementBoundsByName.get(normalizeKey(cell.metricName()));
                LocationDashboardWorksheetSample worksheetSample =
                    buildWorksheetSample(cell, row, rowContext, measurementBound);
                if (worksheetSample != null) {
                    sampleBuckets.add(worksheetSample);
                }

                if (!hasUsableCommentPayload(cell.commentText()) || rowContext.systemType() == null) {
                    continue;
                }

                LocationDashboardCommentParser.ParsedComment parsedComment = parseComment(cell, row);
                if (enableCommentDerivedCorrectiveActions) {
                    validatePrimaryCommentSample(parsedComment, cell, row);
                }
                for (LocationDashboardImportedSample sample : extractCommentSamples(
                    parsedComment,
                    cell,
                    row,
                    rowContext,
                    measurementBound
                )) {
                    sampleBuckets.add(sample);
                }
            }

            activeContext = contextResolver.advance(row, rowContext, activeContext);
        }

        return new SampleImportResult(sampleBuckets);
    }

    private LocationDashboardWorksheetSample buildWorksheetSample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        MeasurementBound measurementBound
    ) {
        if (cell == null
            || cell.numericValue() == null
            || rowContext.systemType() == null
            || measurementBound == null
            || rowContext.facilityName() == null) {
            return null;
        }
        return new LocationDashboardWorksheetSample(
            cell.observedDate(),
            cell.numericValue(),
            resolveMeasurementName(cell.metricName(), measurementBound),
            rowContext.facilityName(),
            rowContext.sublocation(),
            rowContext.systemType(),
            measurementBound,
            rowContext.resolvedBuilding(),
            rowContext.resolvedSystem(),
            row == null ? null : row.pointOfUse(),
            row == null ? null : row.basis(),
            cell.cellReference()
        );
    }

    private List<LocationDashboardImportedSample> extractCommentSamples(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        MeasurementBound measurementBound
    ) {
        if (parsedComment == null
            || primaryCell == null
            || measurementBound == null
            || rowContext.systemType() == null
            || rowContext.facilityName() == null) {
            return List.of();
        }

        String measurementName = resolveMeasurementName(primaryCell.metricName(), measurementBound);
        List<LocationDashboardImportedSample> samples = new ArrayList<>();
        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        if (primarySample != null
            && primarySample.sampledOn() != null
            && primarySample.resultValue() != null
            && !matchesWorksheetPrimarySample(primaryCell, primarySample)) {
            samples.add(new LocationDashboardCommentSample(
                LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY,
                primarySample.sampledOn(),
                primarySample.resultValue(),
                measurementName,
                rowContext.facilityName(),
                rowContext.sublocation(),
                rowContext.systemType(),
                measurementBound,
                rowContext.resolvedBuilding(),
                rowContext.resolvedSystem(),
                row == null ? null : row.pointOfUse(),
                row == null ? null : row.basis(),
                primaryCell.cellReference(),
                parsedComment,
                primarySample,
                "Primary Sample",
                buildCommentSampleIdentity("primary-sample", primarySample)
            ));
        }

        int sampleIndex = 1;
        for (LocationDashboardCommentParser.ParsedCommentSample sample : parsedComment.followUpSamples()) {
            if (sample == null || sample.sampledOn() == null || sample.resultValue() == null) {
                sampleIndex += 1;
                continue;
            }
            samples.add(new LocationDashboardCommentSample(
                LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL,
                sample.sampledOn(),
                sample.resultValue(),
                measurementName,
                rowContext.facilityName(),
                rowContext.sublocation(),
                rowContext.systemType(),
                measurementBound,
                rowContext.resolvedBuilding(),
                rowContext.resolvedSystem(),
                row == null ? null : row.pointOfUse(),
                row == null ? null : row.basis(),
                primaryCell.cellReference(),
                parsedComment,
                sample,
                "Supplemental Sample " + sampleIndex,
                buildCommentSampleIdentity("supplemental-sample-" + sampleIndex, sample)
            ));
            sampleIndex += 1;
        }
        return List.copyOf(samples);
    }

    private LocationDashboardCommentParser.ParsedComment parseComment(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row
    ) {
        try {
            return commentParser.parse(cell.commentText());
        } catch (IllegalArgumentException ex) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": " + ex.getMessage()
            );
        }
    }

    private void validatePrimaryCommentSample(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row
    ) {
        if (parsedComment == null || !parsedComment.structured() || parsedComment.primarySample() == null) {
            return;
        }
        if (cell.numericValue() != null
            && parsedComment.hasMigratedLabeledTestNotes()
            && parsedComment.matchesWorksheetCompatibilityValue(cell.numericValue())) {
            return;
        }

        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        if (cell.observedDate() != null
            && primarySample.sampledOn() != null
            && !sameObservationMonth(cell.observedDate(), primarySample.sampledOn())) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": comment primary sample date must stay within the worksheet month bucket."
            );
        }
        if (cell.numericValue() != null
            && primarySample.resultValue() != null
            && cell.numericValue().compareTo(primarySample.resultValue()) != 0) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": comment primary sample result does not match the worksheet cell."
            );
        }
    }

    private boolean hasUsableCommentPayload(String rawCommentText) {
        if (rawCommentText == null || rawCommentText.isBlank()) {
            return false;
        }
        String normalized = rawCommentText.replace("\r\n", "\n").strip();
        int markerIndex = normalized.indexOf("Comment:");
        String payload = markerIndex >= 0
            ? normalized.substring(markerIndex + "Comment:".length()).strip()
            : normalized;
        return !payload.isBlank();
    }

    private boolean matchesWorksheetPrimarySample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardCommentParser.ParsedCommentSample candidateSample
    ) {
        if (primaryCell == null || candidateSample == null) {
            return false;
        }
        if (primaryCell.observedDate() == null || candidateSample.sampledOn() == null) {
            return false;
        }
        if (!sameObservationMonth(primaryCell.observedDate(), candidateSample.sampledOn())) {
            return false;
        }
        if (primaryCell.numericValue() == null || candidateSample.resultValue() == null) {
            return false;
        }
        return primaryCell.numericValue().compareTo(candidateSample.resultValue()) == 0;
    }

    private boolean sameObservationMonth(LocalDate worksheetObservedDate, LocalDate commentSampleDate) {
        if (worksheetObservedDate == null || commentSampleDate == null) {
            return false;
        }
        return worksheetObservedDate.getYear() == commentSampleDate.getYear()
            && worksheetObservedDate.getMonth() == commentSampleDate.getMonth();
    }

    private String resolveMeasurementName(String workbookMetricName, MeasurementBound measurementBound) {
        if (workbookMetricName != null && !workbookMetricName.isBlank()) {
            return workbookMetricName.strip();
        }
        if (measurementBound == null || measurementBound.getMeasurementName() == null) {
            return null;
        }
        String fallbackMeasurementName = measurementBound.getMeasurementName().strip();
        return fallbackMeasurementName.isBlank() ? null : fallbackMeasurementName;
    }

    private String buildCommentSampleIdentity(
        String sampleKind,
        LocationDashboardCommentParser.ParsedCommentSample sample
    ) {
        return String.join("|", List.of(
            nullSafe(sampleKind),
            nullSafe(String.valueOf(sample == null ? null : sample.sampledOn())),
            nullSafe(String.valueOf(sample == null ? null : sample.resultReceivedOn())),
            nullSafe(sample == null ? null : sample.resultRaw())
        ));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private ApiClientException invalidSpreadsheet(String message) {
        return new ApiClientException(
            HttpStatus.BAD_REQUEST,
            "location_dashboard_file_invalid",
            message
        );
    }

    private static String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }

    record SampleImportResult(
        LocationDashboardSampleBuckets sampleBuckets
    ) {
    }
}
