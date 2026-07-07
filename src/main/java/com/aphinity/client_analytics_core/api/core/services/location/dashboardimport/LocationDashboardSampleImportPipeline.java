package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LocationDashboardSampleImportPipeline {
    private static final long FOLLOW_UP_DUPLICATE_DATE_MARGIN_DAYS = 14L;

    private final LocationDashboardImportContextResolver contextResolver;
    private final LocationDashboardCommentParser commentParser;

    LocationDashboardSampleImportPipeline(
        LocationDashboardImportContextResolver contextResolver,
        LocationDashboardCommentParser commentParser
    ) {
        this.contextResolver = contextResolver;
        this.commentParser = commentParser;
    }

    SampleImportResult importSamples(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        Map<String, MeasurementBound> measurementBoundsByName
    ) {
        LocationDashboardSampleBuckets sampleBuckets = new LocationDashboardSampleBuckets();
        List<PreparedCellImport> preparedCells = prepareCellImports(workbook, measurementBoundsByName);
        Set<String> deduplicatedWorksheetCells = detectFollowUpWorksheetDuplicates(preparedCells);

        for (PreparedCellImport preparedCell : preparedCells) {
            if (!deduplicatedWorksheetCells.contains(preparedCell.worksheetCellIdentity())) {
                LocationDashboardWorksheetSample worksheetSample = buildWorksheetSample(
                    preparedCell.cell(),
                    preparedCell.row(),
                    preparedCell.rowContext(),
                    preparedCell.measurementBound(),
                    preparedCell.parsedComment()
                );
                if (worksheetSample != null) {
                    sampleBuckets.add(worksheetSample);
                }
            }

            if (preparedCell.parsedComment() == null) {
                continue;
            }

            validatePrimaryCommentSample(preparedCell.parsedComment(), preparedCell.cell(), preparedCell.row());
            for (LocationDashboardImportedSample sample : extractCommentSamples(
                preparedCell.parsedComment(),
                preparedCell.cell(),
                preparedCell.row(),
                preparedCell.rowContext(),
                preparedCell.measurementBound()
            )) {
                sampleBuckets.add(sample);
            }
        }

        return new SampleImportResult(sampleBuckets);
    }

    private List<PreparedCellImport> prepareCellImports(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        Map<String, MeasurementBound> measurementBoundsByName
    ) {
        List<PreparedCellImport> preparedCells = new ArrayList<>();
        LocationDashboardImportContextResolver.ActiveImportContext activeContext = contextResolver.emptyContext();
        int sequenceIndex = 0;

        for (LocationDashboardSpreadsheetParser.ParsedDashboardRow row : workbook.rows()) {
            LocationDashboardImportContextResolver.RowImportContext rowContext =
                contextResolver.resolveRowContext(row, activeContext);

            for (LocationDashboardSpreadsheetParser.ParsedDashboardCell cell : row.cells()) {
                MeasurementBound measurementBound = measurementBoundsByName.get(normalizeKey(cell.metricName()));
                LocationDashboardCommentParser.ParsedComment parsedComment = null;
                if (hasUsableCommentPayload(cell.commentText()) && rowContext.systemType() != null) {
                    parsedComment = parseComment(cell, row);
                }
                preparedCells.add(new PreparedCellImport(
                    row,
                    cell,
                    rowContext,
                    measurementBound,
                    parsedComment,
                    worksheetCellIdentity(row, cell),
                    sequenceIndex
                ));
                sequenceIndex += 1;
            }

            activeContext = contextResolver.advance(row, rowContext, activeContext);
        }

        return List.copyOf(preparedCells);
    }

    private Set<String> detectFollowUpWorksheetDuplicates(List<PreparedCellImport> preparedCells) {
        Set<String> deduplicatedWorksheetCells = new HashSet<>();
        Set<String> consumedWorksheetCells = new HashSet<>();

        for (PreparedCellImport sourceCell : preparedCells) {
            LocationDashboardCommentParser.ParsedComment parsedComment = sourceCell.parsedComment();
            if (parsedComment == null || parsedComment.followUpSamples().isEmpty()) {
                continue;
            }

            String measurementName = resolveMeasurementName(sourceCell.cell().metricName(), sourceCell.measurementBound());
            String streamIdentity = worksheetStreamIdentity(sourceCell.row(), sourceCell.rowContext(), measurementName);
            if (streamIdentity == null) {
                continue;
            }

            for (LocationDashboardCommentParser.ParsedCommentSample followUpSample : parsedComment.followUpSamples()) {
                if (followUpSample == null
                    || followUpSample.sampledOn() == null
                    || followUpSample.resultValue() == null) {
                    continue;
                }

                PreparedCellImport duplicateWorksheet = findFutureWorksheetDuplicate(
                    sourceCell,
                    followUpSample,
                    streamIdentity,
                    preparedCells,
                    consumedWorksheetCells
                );
                if (duplicateWorksheet != null) {
                    String worksheetIdentity = duplicateWorksheet.worksheetCellIdentity();
                    deduplicatedWorksheetCells.add(worksheetIdentity);
                    consumedWorksheetCells.add(worksheetIdentity);
                }
            }
        }

        return Set.copyOf(deduplicatedWorksheetCells);
    }

    private PreparedCellImport findFutureWorksheetDuplicate(
        PreparedCellImport sourceCell,
        LocationDashboardCommentParser.ParsedCommentSample followUpSample,
        String sourceStreamIdentity,
        List<PreparedCellImport> preparedCells,
        Set<String> consumedWorksheetCells
    ) {
        for (PreparedCellImport candidate : preparedCells) {
            if (candidate.sequenceIndex() <= sourceCell.sequenceIndex()) {
                continue;
            }
            if (consumedWorksheetCells.contains(candidate.worksheetCellIdentity())) {
                continue;
            }
            if (!Objects.equals(
                sourceStreamIdentity,
                worksheetStreamIdentity(
                    candidate.row(),
                    candidate.rowContext(),
                    resolveMeasurementName(candidate.cell().metricName(), candidate.measurementBound())
                )
            )) {
                continue;
            }
            if (!matchesWorksheetFollowUpSample(candidate, followUpSample)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private LocationDashboardWorksheetSample buildWorksheetSample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        MeasurementBound measurementBound,
        LocationDashboardCommentParser.ParsedComment parsedComment
    ) {
        if (cell == null
            || cell.numericValue() == null
            || rowContext.systemType() == null
            || measurementBound == null
            || rowContext.facilityName() == null) {
            return null;
        }
        String measurementName = resolveMeasurementName(cell.metricName(), measurementBound);
        return new LocationDashboardWorksheetSample(
            effectiveWorksheetObservedDate(cell, parsedComment),
            cell.numericValue(),
            measurementName,
            rowContext.facilityName(),
            rowContext.sublocation(),
            rowContext.systemType(),
            measurementBound,
            rowContext.resolvedBuilding(),
            rowContext.resolvedSystem(),
            row == null ? null : row.pointOfUse(),
            row == null ? null : row.basis(),
            cell.rawValue(),
            commentParser.unitForMeasurementName(measurementName),
            cell.cellReference(),
            parsedComment
        );
    }

    private LocalDate effectiveWorksheetObservedDate(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardCommentParser.ParsedComment parsedComment
    ) {
        if (cell == null) {
            return null;
        }
        LocationDashboardCommentParser.ParsedCommentSample primarySample =
            parsedComment == null ? null : parsedComment.primarySample();
        if (primarySample != null
            && primarySample.sampledOn() != null
            && matchesWorksheetPrimarySample(cell, primarySample)) {
            return primarySample.sampledOn();
        }
        return cell.observedDate();
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
        String units = commentParser.unitForMeasurementName(measurementName);
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
                primarySample.resultRaw(),
                units,
                primaryCell.cellReference(),
                parsedComment,
                primarySample,
                "Primary Sample",
                buildCommentSampleIdentity(
                    "primary-sample",
                    primarySample,
                    primaryCell,
                    row,
                    rowContext,
                    measurementName
                )
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
                sample.resultRaw(),
                units,
                primaryCell.cellReference(),
                parsedComment,
                sample,
                "Supplemental Sample " + sampleIndex,
                buildCommentSampleIdentity(
                    "supplemental-sample-" + sampleIndex,
                    sample,
                    primaryCell,
                    row,
                    rowContext,
                    measurementName
                )
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
        String cellReference = cell.cellReference() == null ? "" : " cell " + cell.cellReference();
        if (cell.observedDate() != null
            && primarySample.sampledOn() != null
            && sameObservationMonth(cell.observedDate(), primarySample.sampledOn())) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + cellReference
                    + ": comment primary sample date must stay within the worksheet month bucket."
            );
        }
        if (cell.numericValue() != null
            && primarySample.resultValue() != null
            && cell.numericValue().compareTo(primarySample.resultValue()) != 0) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + cellReference
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
        if (sameObservationMonth(primaryCell.observedDate(), candidateSample.sampledOn())) {
            return false;
        }
        if (primaryCell.numericValue() == null || candidateSample.resultValue() == null) {
            return false;
        }
        return primaryCell.numericValue().compareTo(candidateSample.resultValue()) == 0;
    }

    private boolean matchesWorksheetFollowUpSample(
        PreparedCellImport worksheetCell,
        LocationDashboardCommentParser.ParsedCommentSample followUpSample
    ) {
        if (worksheetCell == null || worksheetCell.cell() == null || followUpSample == null) {
            return false;
        }
        if (worksheetCell.cell().numericValue() == null || followUpSample.resultValue() == null) {
            return false;
        }
        if (worksheetCell.cell().numericValue().compareTo(followUpSample.resultValue()) != 0) {
            return false;
        }
        LocalDate effectiveObservedDate = effectiveWorksheetObservedDate(worksheetCell.cell(), worksheetCell.parsedComment());
        if (effectiveObservedDate == null) {
            return false;
        }

        return isWithinDuplicateDateMargin(effectiveObservedDate, followUpSample.resultReceivedOn());
    }

    private boolean isWithinDuplicateDateMargin(LocalDate worksheetObservedDate, LocalDate commentDate) {
        if (worksheetObservedDate == null || commentDate == null) {
            return false;
        }
        return Math.abs(ChronoUnit.DAYS.between(commentDate, worksheetObservedDate))
            <= FOLLOW_UP_DUPLICATE_DATE_MARGIN_DAYS;
    }

    private boolean sameObservationMonth(LocalDate worksheetObservedDate, LocalDate commentSampleDate) {
        if (worksheetObservedDate == null || commentSampleDate == null) {
            return true;
        }
        return worksheetObservedDate.getYear() != commentSampleDate.getYear()
                || worksheetObservedDate.getMonth() != commentSampleDate.getMonth();
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
        LocationDashboardCommentParser.ParsedCommentSample sample,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        String measurementName
    ) {
        return String.join("|", List.of(
            nullSafe(sampleKind),
            nullSafe(rowContext == null ? null : rowContext.facilityName()),
            nullSafe(rowContext == null ? null : rowContext.resolvedBuilding()),
            nullSafe(rowContext == null ? null : rowContext.resolvedSystem()),
            nullSafe(measurementName),
            nullSafe(row == null ? null : row.pointOfUse()),
            nullSafe(row == null ? null : row.basis()),
            nullSafe(primaryCell == null ? null : primaryCell.cellReference()),
            nullSafe(String.valueOf(sample == null ? null : sample.sampledOn())),
            nullSafe(String.valueOf(sample == null ? null : sample.resultReceivedOn())),
            nullSafe(sample == null ? null : sample.resultRaw())
        ));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String worksheetStreamIdentity(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        String measurementName
    ) {
        if (rowContext == null || rowContext.facilityName() == null || measurementName == null) {
            return null;
        }
        return String.join("|", List.of(
            nullSafe(rowContext.facilityName()),
            nullSafe(rowContext.resolvedBuilding()),
            nullSafe(rowContext.resolvedSystem()),
            nullSafe(measurementName),
            nullSafe(row == null ? null : row.pointOfUse()),
            nullSafe(row == null ? null : row.basis())
        ));
    }

    private String worksheetCellIdentity(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell
    ) {
        return String.join("|", List.of(
            nullSafe(String.valueOf(row == null ? null : row.rowNumber())),
            nullSafe(cell == null ? null : cell.cellReference())
        ));
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

    private record PreparedCellImport(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardImportContextResolver.RowImportContext rowContext,
        MeasurementBound measurementBound,
        LocationDashboardCommentParser.ParsedComment parsedComment,
        String worksheetCellIdentity,
        int sequenceIndex
    ) {
    }
}
