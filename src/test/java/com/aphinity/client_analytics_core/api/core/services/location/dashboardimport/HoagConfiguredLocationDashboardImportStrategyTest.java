package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoagConfiguredLocationDashboardImportStrategyTest {
    private static final String DATA_UPLOAD_FIXTURE = "data_upload_test.xlsx";
    private static final int EXPECTED_ADDED_NON_CONFORMANCES = 6;
    private static final List<InjectedCommentPlan> INJECTED_COMMENT_PLANS = List.of(
        new InjectedCommentPlan("F10", 2, List.of(
            new SupplementalFailurePlan(5, new BigDecimal("150")),
            new SupplementalFailurePlan(26, new BigDecimal("175"))
        )),
        new InjectedCommentPlan("G11", 1, List.of(
            new SupplementalFailurePlan(9, new BigDecimal("160"))
        )),
        new InjectedCommentPlan("H12", 3, List.of(
            new SupplementalFailurePlan(6, new BigDecimal("180")),
            new SupplementalFailurePlan(19, new BigDecimal("190")),
            new SupplementalFailurePlan(37, new BigDecimal("210"))
        ))
    );

    private final LocationDashboardSpreadsheetParser parser = new LocationDashboardSpreadsheetParser();
    private final LocationDashboardCommentParser commentParser = new LocationDashboardCommentParser();

    @Test
    void computeImportTracksOnlyTheKnownSupplementalHoagCommentNonConformanceDelta() throws IOException {
        ConfiguredLocationDashboardImportStrategy strategy = resolveHoagStrategy();

        byte[] originalBytes = readFixtureBytes(DATA_UPLOAD_FIXTURE);
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook parsedWorkbook = parseWorkbook(originalBytes);
        SanitizedWorkbook sanitizedWorkbook = sanitizeInvalidFixtureComments(parsedWorkbook);
        Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> originalCellsByReference =
            cellsByReference(sanitizedWorkbook.workbook());

        assertTrue(sanitizedWorkbook.normalizedCellReferences().contains("BH43"));
        assertTrue(sanitizedWorkbook.normalizedCellReferences().contains("CB38"));

        for (InjectedCommentPlan plan : INJECTED_COMMENT_PLANS) {
            LocationDashboardSpreadsheetParser.ParsedDashboardCell originalCell =
                originalCellsByReference.get(plan.cellReference());
            assertTrue(originalCell != null, () -> "Expected fixture cell " + plan.cellReference() + " to exist");
            assertNull(
                originalCell.commentText(),
                () -> "Expected fixture cell " + plan.cellReference() + " to start without a comment"
            );
        }

        LocationDashboardImportStrategy.LocationDashboardImportComputation baseline =
            strategy.computeImport(sanitizedWorkbook.workbook(), hoagMeasurementBounds());

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook augmentedWorkbook =
            injectSupplementalHoagComments(sanitizedWorkbook.workbook(), originalCellsByReference);
        Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> augmentedCellsByReference =
            cellsByReference(augmentedWorkbook);

        LocationDashboardImportStrategy.LocationDashboardImportComputation augmented =
            strategy.computeImport(augmentedWorkbook, hoagMeasurementBounds());

        assertEquals(
            baseline.correctiveActions().size(),
            augmented.correctiveActions().size()
        );
        assertEquals(
            baseline.observations().size() + EXPECTED_ADDED_NON_CONFORMANCES,
            augmented.observations().size()
        );
        assertEquals(
            baseline.analyzedSamples().stream().filter(LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming).count()
                + EXPECTED_ADDED_NON_CONFORMANCES,
            augmented.analyzedSamples().stream().filter(LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming).count()
        );

        for (InjectedCommentPlan plan : INJECTED_COMMENT_PLANS) {
            String marker = markerText(plan);
            LocationDashboardSpreadsheetParser.ParsedDashboardCell augmentedCell =
                augmentedCellsByReference.get(plan.cellReference());
            assertTrue(augmentedCell != null && augmentedCell.commentText() != null);
            assertTrue(augmentedCell.commentText().contains(marker));
        }
    }

    private ConfiguredLocationDashboardImportStrategy resolveHoagStrategy() {
        return (ConfiguredLocationDashboardImportStrategy) new LocationDashboardImportStrategyRegistry()
            .resolve("Hoag Hospital")
            .orElseThrow(() -> new AssertionError("Expected Hoag Hospital dashboard strategy to load"));
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook parseWorkbook(byte[] bytes) {
        return parser.parse(new MockMultipartFile(
            "file",
            DATA_UPLOAD_FIXTURE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        ));
    }

    private byte[] readFixtureBytes(String fileName) throws IOException {
        try (var inputStream = new ClassPathResource(fileName).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> cellsByReference(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook
    ) {
        return workbook.rows().stream()
            .flatMap(row -> row.cells().stream())
            .filter(cell -> cell.cellReference() != null)
            .collect(LinkedHashMap::new, (map, cell) -> map.put(cell.cellReference(), cell), Map::putAll);
    }

    private SanitizedWorkbook sanitizeInvalidFixtureComments(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook originalWorkbook
    ) {
        Set<String> normalizedCellReferences = new LinkedHashSet<>();
        List<LocationDashboardSpreadsheetParser.ParsedDashboardRow> sanitizedRows = originalWorkbook.rows().stream()
            .map(row -> new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                row.rowNumber(),
                row.facility(),
                row.building(),
                row.system(),
                row.pointOfUse(),
                row.basis(),
                row.cells().stream()
                    .map(cell -> sanitizeCellComment(cell, normalizedCellReferences))
                    .toList()
            ))
            .toList();

        return new SanitizedWorkbook(
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                originalWorkbook.locationTitle(),
                sanitizedRows
            ),
            Set.copyOf(normalizedCellReferences)
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardCell sanitizeCellComment(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        Set<String> normalizedCellReferences
    ) {
        String commentText = cell.commentText();
        if (commentText == null || commentText.isBlank()) {
            return cell;
        }

        LocationDashboardCommentParser.ParsedComment parsedComment;
        try {
            parsedComment = commentParser.parse(commentText);
        } catch (IllegalArgumentException ex) {
            return cell;
        }
        if (!parsedComment.structured() || parsedComment.primarySample() == null) {
            return cell;
        }

        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        boolean monthMismatch = cell.observedDate() != null
            && primarySample.sampledOn() != null
            && !sameObservationMonth(cell.observedDate(), primarySample.sampledOn());
        boolean resultMismatch = cell.numericValue() != null
            && primarySample.resultValue() != null
            && cell.numericValue().compareTo(primarySample.resultValue()) != 0;
        if (!monthMismatch && !resultMismatch) {
            return cell;
        }

        normalizedCellReferences.add(cell.cellReference());
        LocationDashboardCommentParser.ParsedCommentSample correctedPrimarySample =
            new LocationDashboardCommentParser.ParsedCommentSample(
                monthMismatch ? alignToWorksheetMonth(cell.observedDate(), primarySample.sampledOn()) : primarySample.sampledOn(),
                primarySample.resultReceivedOn(),
                resultMismatch ? cell.rawValue() : primarySample.resultRaw(),
                resultMismatch ? cell.numericValue() : primarySample.resultValue(),
                primarySample.resultUnit(),
                primarySample.notes(),
                primarySample.correctiveActions()
            );
        LocationDashboardCommentParser.ParsedComment correctedComment =
            new LocationDashboardCommentParser.ParsedComment(
                true,
                parsedComment.sampleLocation(),
                correctedPrimarySample,
                parsedComment.followUpSamples(),
                parsedComment.correctiveActions(),
                parsedComment.notes()
            );

        return new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
            cell.metricName(),
            cell.observedDate(),
            cell.rawValue(),
            cell.numericValue(),
            toStructuredCommentJson(correctedComment),
            cell.cellReference()
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook injectSupplementalHoagComments(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook originalWorkbook,
        Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> originalCellsByReference
    ) {
        Map<String, String> commentOverridesByReference = new LinkedHashMap<>();
        for (InjectedCommentPlan plan : INJECTED_COMMENT_PLANS) {
            LocationDashboardSpreadsheetParser.ParsedDashboardCell cell =
                originalCellsByReference.get(plan.cellReference());
            commentOverridesByReference.put(plan.cellReference(), buildStructuredComment(cell, plan));
        }

        List<LocationDashboardSpreadsheetParser.ParsedDashboardRow> augmentedRows = originalWorkbook.rows().stream()
            .map(row -> new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                row.rowNumber(),
                row.facility(),
                row.building(),
                row.system(),
                row.pointOfUse(),
                row.basis(),
                row.cells().stream()
                    .map(cell -> new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                        cell.metricName(),
                        cell.observedDate(),
                        cell.rawValue(),
                        cell.numericValue(),
                        commentOverridesByReference.getOrDefault(cell.cellReference(), cell.commentText()),
                        cell.cellReference()
                    ))
                    .toList()
            ))
            .toList();

        return new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
            originalWorkbook.locationTitle(),
            augmentedRows
        );
    }

    private String buildStructuredComment(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        InjectedCommentPlan plan
    ) {
        LocalDate primarySampledOn = withDayOfMonth(cell.observedDate(), 15);
        StringBuilder followUpSamples = new StringBuilder();
        for (int index = 0; index < plan.followUpSamples().size(); index += 1) {
            SupplementalFailurePlan supplementalPlan = plan.followUpSamples().get(index);
            LocalDate sampledOn = primarySampledOn.plusDays(supplementalPlan.dayOffset());
            if (index > 0) {
                followUpSamples.append(",\n");
            }
            followUpSamples.append("""
                {
                  "sampledOn": "%s",
                  "resultReceivedOn": "%s",
                  "resultRaw": "%s",
                  "resultValue": %s,
                  "resultUnit": "CFU.mL",
                  "notes": [],
                  "correctiveActions": [],
                  "sampleLocation": null
                }""".formatted(
                sampledOn,
                sampledOn.plusDays(4),
                supplementalPlan.resultValue().toPlainString() + " CFU.mL",
                jsonNumber(supplementalPlan.resultValue())
            ));
        }

        return """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Parser Delta Control %s",
              "primarySample": {
                "sampledOn": "%s",
                "resultReceivedOn": "%s",
                "resultRaw": "%s",
                "resultValue": %s,
                "resultUnit": null,
                "notes": [],
                "correctiveActions": [],
                "sampleLocation": null
              },
              "followUpSamples": [
            %s
              ],
              "correctiveActions": [],
              "notes": [
                "%s"
              ]
            }
            """.formatted(
            plan.cellReference(),
            primarySampledOn,
            primarySampledOn.plusDays(4),
            escapeJson(cell.rawValue()),
            jsonNumber(cell.numericValue()),
            followUpSamples,
            markerText(plan)
        );
    }

    private String toStructuredCommentJson(LocationDashboardCommentParser.ParsedComment parsedComment) {
        String primarySampleJson = toSampleJson(parsedComment.primarySample(), 2);
        String followUpSamplesJson = parsedComment.followUpSamples().stream()
            .map(sample -> toSampleJson(sample, 2))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");
        String correctiveActionsJson = parsedComment.correctiveActions().stream()
            .map(action -> toCorrectiveActionJson(action, 2))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");
        String notesJson = parsedComment.notes().stream()
            .map(note -> indent(2) + jsonString(note))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");

        return """
            {
              "schema": %s,
              "sampleLocation": %s,
              "primarySample": %s,
              "followUpSamples": [
            %s
              ],
              "correctiveActions": [
            %s
              ],
              "notes": [
            %s
              ]
            }
            """.formatted(
            jsonString(LocationDashboardCommentParser.SCHEMA_VERSION),
            jsonStringOrNull(parsedComment.sampleLocation()),
            primarySampleJson,
            followUpSamplesJson,
            correctiveActionsJson,
            notesJson
        );
    }

    private String toSampleJson(LocationDashboardCommentParser.ParsedCommentSample sample, int indentLevel) {
        if (sample == null) {
            return "null";
        }
        String notesJson = sample.notes().stream()
            .map(note -> indent(indentLevel + 2) + jsonString(note))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");
        String correctiveActionsJson = sample.correctiveActions().stream()
            .map(action -> toCorrectiveActionJson(action, indentLevel + 2))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");

        return """
            {
            %s"sampledOn": %s,
            %s"resultReceivedOn": %s,
            %s"resultRaw": %s,
            %s"resultValue": %s,
            %s"resultUnit": %s,
            %s"notes": [
            %s
            %s],
            %s"correctiveActions": [
            %s
            %s]
            %s}""".formatted(
            indent(indentLevel + 1), jsonStringOrNull(sample.sampledOn()),
            indent(indentLevel + 1), jsonStringOrNull(sample.resultReceivedOn()),
            indent(indentLevel + 1), jsonStringOrNull(sample.resultRaw()),
            indent(indentLevel + 1), jsonNumberOrNull(sample.resultValue()),
            indent(indentLevel + 1), jsonStringOrNull(sample.resultUnit()),
            indent(indentLevel + 1),
            notesJson,
            indent(indentLevel + 1),
            indent(indentLevel + 1),
            correctiveActionsJson,
            indent(indentLevel + 1),
            indent(indentLevel)
        );
    }

    private String toCorrectiveActionJson(
        LocationDashboardCommentParser.ParsedCommentCorrectiveAction action,
        int indentLevel
    ) {
        String notesJson = action.notes().stream()
            .map(note -> indent(indentLevel + 2) + jsonString(note))
            .reduce((left, right) -> left + ",\n" + right)
            .orElse("");
        return """
            {
            %s"actionDate": %s,
            %s"text": %s,
            %s"ticket": %s,
            %s"notes": [
            %s
            %s]
            %s}""".formatted(
            indent(indentLevel + 1), jsonStringOrNull(action.actionDate()),
            indent(indentLevel + 1), jsonStringOrNull(action.text()),
            indent(indentLevel + 1), jsonStringOrNull(action.ticket()),
            indent(indentLevel + 1),
            notesJson,
            indent(indentLevel + 1),
            indent(indentLevel)
        );
    }

    private String markerText(InjectedCommentPlan plan) {
        return "Parser delta control " + plan.cellReference()
            + ": this comment adds exactly " + plan.addedNonConformances() + " non-conformance"
            + (plan.addedNonConformances() == 1 ? "" : "s") + ".";
    }

    private LocalDate withDayOfMonth(LocalDate date, int dayOfMonth) {
        return date.withDayOfMonth(Math.min(dayOfMonth, date.lengthOfMonth()));
    }

    private LocalDate alignToWorksheetMonth(LocalDate worksheetObservedDate, LocalDate primarySampleDate) {
        if (worksheetObservedDate == null) {
            return primarySampleDate;
        }
        if (primarySampleDate == null) {
            return worksheetObservedDate;
        }
        return LocalDate.of(
            worksheetObservedDate.getYear(),
            worksheetObservedDate.getMonth(),
            Math.min(primarySampleDate.getDayOfMonth(), worksheetObservedDate.lengthOfMonth())
        );
    }

    private boolean sameObservationMonth(LocalDate worksheetObservedDate, LocalDate commentSampleDate) {
        if (worksheetObservedDate == null || commentSampleDate == null) {
            return false;
        }
        return worksheetObservedDate.getYear() == commentSampleDate.getYear()
            && worksheetObservedDate.getMonth() == commentSampleDate.getMonth();
    }

    private String jsonNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String jsonNumberOrNull(BigDecimal value) {
        return value == null ? "null" : jsonNumber(value);
    }

    private String jsonStringOrNull(Object value) {
        return value == null ? "null" : jsonString(value.toString());
    }

    private String jsonString(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private String indent(int level) {
        return "  ".repeat(Math.max(0, level));
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private List<MeasurementBound> hoagMeasurementBounds() {
        return List.of(
            hoagMeasurementBound(1L, "HPC"),
            hoagMeasurementBound(2L, "Endotoxin"),
            hoagMeasurementBound(3L, "Legionella"),
            hoagMeasurementBound(4L, "pH"),
            hoagMeasurementBound(5L, "Conductivity"),
            hoagMeasurementBound(6L, "Alkalinity"),
            hoagMeasurementBound(7L, "Hardness")
        );
    }

    private MeasurementBound hoagMeasurementBound(Long id, String measurementName) {
        MeasurementBound measurementBound = new MeasurementBound();
        measurementBound.setId(id);
        measurementBound.setMeasurementName(measurementName);
        measurementBound.setCriticalRangeMax(new BigDecimal("100"));
        measurementBound.setUtilityRangeMax(new BigDecimal("100"));
        measurementBound.setPotableRangeMax(new BigDecimal("100"));
        measurementBound.setTowersRangeMax(new BigDecimal("100"));
        return measurementBound;
    }

    private record InjectedCommentPlan(
        String cellReference,
        int addedNonConformances,
        List<SupplementalFailurePlan> followUpSamples
    ) {
    }

    private record SupplementalFailurePlan(int dayOffset, BigDecimal resultValue) {
    }

    private record SanitizedWorkbook(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        Set<String> normalizedCellReferences
    ) {
    }
}
