package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HoagConfiguredLocationDashboardImportStrategyTest {
    private static final String DATA_UPLOAD_FIXTURE = "dashboard_upload_template_example_2.xlsx";
    private static final int EXPECTED_ADDED_NON_CONFORMANCES = 6;
    private static final List<InjectedCommentPlan> INJECTED_COMMENT_PLANS = List.of(
        new InjectedCommentPlan(2, List.of(
            new SupplementalFailurePlan(5, new BigDecimal("150")),
            new SupplementalFailurePlan(26, new BigDecimal("175"))
        )),
        new InjectedCommentPlan(1, List.of(
            new SupplementalFailurePlan(9, new BigDecimal("160"))
        )),
        new InjectedCommentPlan(3, List.of(
            new SupplementalFailurePlan(6, new BigDecimal("180")),
            new SupplementalFailurePlan(19, new BigDecimal("190")),
            new SupplementalFailurePlan(37, new BigDecimal("210"))
        ))
    );

    private final LocationDashboardSpreadsheetParser parser = new LocationDashboardSpreadsheetParser();

    @Test
    void computeImportTracksOnlyTheKnownSupplementalHoagCommentNonConformanceDelta() throws IOException {
        ConfiguredLocationDashboardImportStrategy strategy = resolveHoagStrategy();

        byte[] originalBytes = readFixtureBytes(DATA_UPLOAD_FIXTURE);
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook parsedWorkbook = parseWorkbook(originalBytes);
        Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> originalCellsByReference =
            cellsByReference(parsedWorkbook);
        List<ResolvedInjectedCommentPlan> resolvedPlans = resolveInjectedCommentPlans(parsedWorkbook);

        for (ResolvedInjectedCommentPlan plan : resolvedPlans) {
            LocationDashboardSpreadsheetParser.ParsedDashboardCell originalCell =
                originalCellsByReference.get(plan.cellReference());
            assertNotNull(originalCell, () -> "Expected fixture cell " + plan.cellReference() + " to exist");
            assertNull(
                originalCell.commentText(),
                () -> "Expected fixture cell " + plan.cellReference() + " to start without a comment"
            );
        }

        LocationDashboardImportStrategy.LocationDashboardImportComputation baseline =
            strategy.computeImport(parsedWorkbook, hoagMeasurementBounds());

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook augmentedWorkbook =
            injectSupplementalHoagComments(parsedWorkbook, originalCellsByReference, resolvedPlans);
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

        for (ResolvedInjectedCommentPlan plan : resolvedPlans) {
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

    private List<ResolvedInjectedCommentPlan> resolveInjectedCommentPlans(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook
    ) {
        List<LocationDashboardSpreadsheetParser.ParsedDashboardCell> injectableCells = workbook.rows().stream()
            .flatMap(row -> row.cells().stream())
            .filter(cell -> cell.cellReference() != null)
            .filter(cell -> cell.numericValue() != null)
            .filter(cell -> cell.commentText() == null)
            .limit(INJECTED_COMMENT_PLANS.size())
            .toList();

        assertEquals(
            INJECTED_COMMENT_PLANS.size(),
            injectableCells.size(),
            "Expected enough comment-free numeric worksheet cells in the Hoag fixture"
        );

        List<ResolvedInjectedCommentPlan> resolvedPlans = new java.util.ArrayList<>();
        for (int index = 0; index < INJECTED_COMMENT_PLANS.size(); index += 1) {
            InjectedCommentPlan plan = INJECTED_COMMENT_PLANS.get(index);
            resolvedPlans.add(new ResolvedInjectedCommentPlan(
                injectableCells.get(index).cellReference(),
                plan.addedNonConformances(),
                plan.followUpSamples()
            ));
        }
        return List.copyOf(resolvedPlans);
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook injectSupplementalHoagComments(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook originalWorkbook,
        Map<String, LocationDashboardSpreadsheetParser.ParsedDashboardCell> originalCellsByReference,
        List<ResolvedInjectedCommentPlan> resolvedPlans
    ) {
        Map<String, String> commentOverridesByReference = new LinkedHashMap<>();
        for (ResolvedInjectedCommentPlan plan : resolvedPlans) {
            LocationDashboardSpreadsheetParser.ParsedDashboardCell cell =
                originalCellsByReference.get(plan.cellReference());
            commentOverridesByReference.put(plan.cellReference(), buildWorkbookComment(cell, plan));
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

    private String buildWorkbookComment(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        ResolvedInjectedCommentPlan plan
    ) {
        LocalDate primarySampledOn = withDayOfMonth(cell.observedDate(), 15);
        return LocationDashboardCommentFixtures.workbookComment(
            new LocationDashboardCommentFixtures.WorkbookCommentSpec(
                "Parser Delta Control " + plan.cellReference(),
                new LocationDashboardCommentFixtures.WorkbookSample(
                    primarySampledOn,
                    primarySampledOn.plusDays(4),
                    cell.rawValue(),
                    cell.numericValue(),
                    null,
                    List.of(),
                    List.of(),
                    null
                ),
                plan.followUpSamples().stream()
                    .map(supplementalPlan -> {
                        LocalDate sampledOn = primarySampledOn.plusDays(supplementalPlan.dayOffset());
                        return new LocationDashboardCommentFixtures.WorkbookSample(
                            sampledOn,
                            sampledOn.plusDays(4),
                            supplementalPlan.resultValue().toPlainString() + " CFU.mL",
                            supplementalPlan.resultValue(),
                            "CFU.mL",
                            List.of(),
                            List.of(),
                            null
                        );
                    })
                    .toList(),
                List.of(),
                List.of(markerText(plan))
            )
        );
    }

    private String markerText(ResolvedInjectedCommentPlan plan) {
        return "Parser delta control " + plan.cellReference()
            + ": this comment adds exactly " + plan.addedNonConformances() + " non-conformance"
            + (plan.addedNonConformances() == 1 ? "" : "s") + ".";
    }

    private LocalDate withDayOfMonth(LocalDate date, int dayOfMonth) {
        return date.withDayOfMonth(Math.min(dayOfMonth, date.lengthOfMonth()));
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
        int addedNonConformances,
        List<SupplementalFailurePlan> followUpSamples
    ) {
    }

    private record ResolvedInjectedCommentPlan(
        String cellReference,
        int addedNonConformances,
        List<SupplementalFailurePlan> followUpSamples
    ) {
    }

    private record SupplementalFailurePlan(int dayOffset, BigDecimal resultValue) {
    }
}
