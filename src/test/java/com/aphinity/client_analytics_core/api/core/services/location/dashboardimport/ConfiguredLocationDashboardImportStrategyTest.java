package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.correctiveAction;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.sample;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.workbookComment;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.workbookStyleComment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredLocationDashboardImportStrategyTest {
    @Test
    void computeImportAggregatesComplianceByMeasurementAndSystemType() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F5");

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(2, result.graphs().size());
        assertEquals(2, result.graphs().getFirst().data().size());
        Map<String, Object> waterQualityHpcTrace = result.graphs().getFirst().data().getFirst();
        assertEquals("HPC", waterQualityHpcTrace.get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityHpcTrace.get("x"));
        assertEquals(List.of(1L), waterQualityHpcTrace.get("y"));
        Map<String, Object> waterQualityEndotoxinTrace = result.graphs().getFirst().data().get(1);
        assertEquals("Endotoxin", waterQualityEndotoxinTrace.get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityEndotoxinTrace.get("x"));
        assertEquals(List.of(1L), waterQualityEndotoxinTrace.get("y"));

        Map<String, Object> systemTypeTrace = result.graphs().get(1).data().getFirst();
        assertEquals("Cooling Towers", systemTypeTrace.get("name"));
        assertEquals(List.of(2L), systemTypeTrace.get("y"));

        assertEquals(4, result.observations().size());
        assertEquals(2, result.observations().stream().filter(LocationDashboardImportStrategy.ImportedObservation::compliant).count());
        assertEquals(2, result.correctiveActions().size());
        assertFalse(result.correctiveActions().stream().anyMatch(draft ->
            draft.description().contains("Drain Tank, install new DI bottles")
        ));
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            "Endotoxin".equals(draft.measurementName())
                && draft.description().contains("out-of-spec worksheet sample without cell comment metadata")
        ));
    }

    @Test
    void computeImportReadsWorkbookCommentSamplesAsSupplementalObservations() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-08-05"),
                "10 CFU.mL",
                new BigDecimal("10"),
                "CFU.mL",
                List.of("30 sec. flush with sample port wipe down."),
                List.of(correctiveAction("New sample port installed"))
            ),
            List.of(sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "5 CFU.mL",
                new BigDecimal("5"),
                "CFU.mL",
                List.of(),
                List.of(correctiveAction("Flush and retest"))
            )),
            List.of(correctiveAction("External note")),
            List.of("General comment note")
        ));

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell(workbookComment, "F5");

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(5, result.observations().size());
        assertTrue(result.observations().stream().anyMatch(observation ->
            LocalDate.parse("2025-08-15").equals(observation.observedDate())
                && "Newport Beach".equals(observation.facilityName())
                && "Cooling Towers".equals(observation.systemTypeName())
        ));
        Map<String, Object> waterQualityHpcTrace = result.graphs().getFirst().data().getFirst();
        assertEquals(List.of("2025-08-01", "2025-08-15"), waterQualityHpcTrace.get("x"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) waterQualityHpcTrace.get("customdata");
        assertEquals(2L, ((Number) customData.getFirst().get("sampleCount")).longValue());
        assertEquals(1L, ((Number) customData.get(1).get("sampleCount")).longValue());
        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            draft.description().contains("External note")
        ));
    }

    @Test
    void computeImportScopesCommentSampleIdentitiesToTheirSourceRow() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "33 CFU.mL",
                new BigDecimal("33"),
                "CFU.mL"
            ),
            List.of(),
            List.of(),
            List.of()
        ));

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        5,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "8",
                            new BigDecimal("8"),
                            workbookComment,
                            "F5"
                        ))
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        6,
                        null,
                        null,
                        "Cooling Towers",
                        "Basin",
                        "CTI/514P",
                        List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "7",
                            new BigDecimal("7"),
                            workbookComment,
                            "F6"
                        ))
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        List<String> commentPrimaryIdentities = result.analyzedSamples().stream()
            .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
            .map(LocationDashboardImportStrategy.AnalyzedSamplePoint::sampleIdentity)
            .toList();

        assertEquals(2, commentPrimaryIdentities.size());
        assertEquals(2L, commentPrimaryIdentities.stream().distinct().count());
        assertTrue(commentPrimaryIdentities.stream().anyMatch(identity -> identity.contains("|F5|")));
        assertTrue(commentPrimaryIdentities.stream().anyMatch(identity -> identity.contains("|F6|")));
    }

    @Test
    void computeImportCreatesCorrectiveActionsForOutOfSpecWorkbookCommentSamples() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-08-05"),
                "10 CFU.mL",
                new BigDecimal("10"),
                "CFU.mL"
            ),
            List.of(sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "15 CFU.mL",
                new BigDecimal("15"),
                "CFU.mL",
                List.of(),
                List.of(correctiveAction("Disinfect and retest"))
            )),
            List.of(),
            List.of()
        ));

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell(workbookComment, "F5");

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Disinfect and retest")
        ));
    }

    @Test
    void computeImportDoesNotDuplicatePrimaryWorkbookCommentSamplesWhenTheyUseActualSampleDatesInsideTheWorksheetMonth() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "10 CFU.mL",
                new BigDecimal("10"),
                "CFU.mL"
            ),
            List.of(),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentCell(workbookComment, "F5"),
            measurementBounds()
        );

        assertEquals(4, result.observations().size());
        assertEquals(2, result.correctiveActions().size());
        assertTrue(result.observations().stream().anyMatch(observation ->
            LocalDate.parse("2025-08-15").equals(observation.observedDate())
                && "HPC".equals(observation.measurementName())
        ));
        assertEquals(
            0L,
            result.analyzedSamples().stream()
                .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
                .count()
        );
        assertEquals(
            1L,
            result.analyzedSamples().stream()
                .filter(sample ->
                    sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                        && LocalDate.parse("2025-08-15").equals(sample.observedDate())
                        && "HPC".equals(sample.measurementName())
                )
                .count()
        );
        Map<String, Object> waterQualityHpcTrace = result.graphs().getFirst().data().getFirst();
        assertEquals(List.of("2025-08-01", "2025-08-15"), waterQualityHpcTrace.get("x"));
        assertFalse(result.correctiveActions().stream().anyMatch(draft ->
            draft.description().contains("Primary Sample: sampled on 2025-08-15")
        ));
    }

    @Test
    void computeImportDoesNotTreatCompliantLabeledTestCommentAsNonConforming() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
                new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                        "Newport Beach",
                        List.of(
                                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                                        5,
                                        "Newport Beach",
                                        "Hospital",
                                        "Cooling Towers",
                                        "Recirc Line",
                                        "CTI/514P",
                                        List.of(
                                                new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                                        "HPC",
                                                        LocalDate.parse("2025-08-01"),
                                                        "9",
                                                        new BigDecimal("9"),
                                                        "First Test: 9",
                                                        "F5"
                                                )
                                        )
                                )
                        )
                );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
                workbook,
                measurementBounds()
        );

        assertEquals(1, result.analyzedSamples().size());
        assertEquals(1, result.observations().size());
        assertEquals(0, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().getFirst().compliant());
        assertFalse(result.analyzedSamples().getFirst().nonConforming());
    }

    @Test
    void computeImportDoesNotTreatCompliantUnstructuredCommentAsNonConforming() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
                new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                        "Newport Beach",
                        List.of(
                                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                                        5,
                                        "Newport Beach",
                                        "Hospital",
                                        "Cooling Towers",
                                        "Recirc Line",
                                        "CTI/514P",
                                        List.of(
                                                new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                                        "HPC",
                                                        LocalDate.parse("2025-08-01"),
                                                        "9",
                                                        new BigDecimal("9"),
                                                        "Routine monitoring note: visual inspection completed with no issues.",
                                                        "F5"
                                                )
                                        )
                                )
                        )
                );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
                workbook,
                measurementBounds()
        );

        assertEquals(1, result.analyzedSamples().size());
        assertEquals(1, result.observations().size());
        assertEquals(0, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().getFirst().compliant());
        assertFalse(result.analyzedSamples().getFirst().nonConforming());
    }

    @Test
    void computeImportDoesNotCreateNonConformanceForCompliantRetestComment() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String commentText = workbookStyleComment(
                "First Test: 9",
                "Retest Sample Date: 8/3/25",
                "Result Date: 8/5/25",
                "Retest Result: 8"
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
                new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                        "Newport Beach",
                        List.of(
                                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                                        5,
                                        "Newport Beach",
                                        "Hospital",
                                        "Cooling Towers",
                                        "Recirc Line",
                                        "CTI/514P",
                                        List.of(
                                                new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                                        "HPC",
                                                        LocalDate.parse("2025-08-01"),
                                                        "9",
                                                        new BigDecimal("9"),
                                                        commentText,
                                                        "F5"
                                                )
                                        )
                                )
                        )
                );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
                workbook,
                measurementBounds()
        );

        assertEquals(2, result.analyzedSamples().size());
        assertEquals(2, result.observations().size());
        assertEquals(0, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().stream().allMatch(
                LocationDashboardImportStrategy.AnalyzedSamplePoint::compliant
        ));
        assertFalse(result.analyzedSamples().stream().anyMatch(
                LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming
        ));
    }

    @Test
    void computeImportDoesNotInheritWorksheetNonConformanceForCompliantFollowUpCommentSample() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String commentText = workbookStyleComment(
                "First Test: 15",
                "Corrective Action Taken on: 8/2/25",
                "Routine flush completed",
                "Retest Sample Date: 8/3/25",
                "Result Date: 8/5/25",
                "Retest Result: 8"
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
                new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                        "Newport Beach",
                        List.of(
                                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                                        5,
                                        "Newport Beach",
                                        "Hospital",
                                        "Cooling Towers",
                                        "Recirc Line",
                                        "CTI/514P",
                                        List.of(
                                                new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                                        "HPC",
                                                        LocalDate.parse("2025-08-01"),
                                                        "15",
                                                        new BigDecimal("15"),
                                                        commentText,
                                                        "F5"
                                                )
                                        )
                                )
                        )
                );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
                workbook,
                measurementBounds()
        );

        assertEquals(2, result.analyzedSamples().size());
        assertEquals(2, result.observations().size());
        assertEquals(1, result.correctiveActions().size());
        assertEquals(1L, result.analyzedSamples().stream().filter(
                LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming
        ).count());
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
                sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                        && LocalDate.parse("2025-08-01").equals(sample.observedDate())
                        && "HPC".equals(sample.measurementName())
                        && sample.nonConforming()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
                sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                        && LocalDate.parse("2025-08-03").equals(sample.observedDate())
                        && "HPC".equals(sample.measurementName())
                        && sample.compliant()
        ));
        assertFalse(result.analyzedSamples().stream().anyMatch(sample ->
                sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                        && sample.nonConforming()
        ));
    }

// ... existing code ...

    @Test
    void computeImportDoesNotCreateDuplicatePrimaryCorrectiveActionsWhenPrimaryCommentSampleMatchesWorksheetMonthAndValue() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "15 CFU.mL",
                new BigDecimal("15"),
                "CFU.mL"
            ),
            List.of(),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentAndPrimaryValue(workbookComment, new BigDecimal("15"), "15", "F5"),
            measurementBounds()
        );

        assertEquals(4, result.observations().size());
        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.observations().stream().anyMatch(observation ->
            LocalDate.parse("2025-08-15").equals(observation.observedDate())
                && "HPC".equals(observation.measurementName())
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                && LocalDate.parse("2025-08-15").equals(sample.observedDate())
                && "HPC".equals(sample.measurementName())
                && sample.nonConforming()
        ));
        assertEquals(
            0L,
            result.analyzedSamples().stream()
                .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
                .count()
        );
        assertEquals(
            2L,
            result.correctiveActions().stream()
                .filter(draft -> "HPC".equals(draft.measurementName()))
                .count()
        );
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
        ));
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-01").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
        ));
        assertFalse(result.correctiveActions().stream().anyMatch(draft ->
            draft.description().contains("Primary Sample: sampled on 2025-08-15")
        ));
    }

    @Test
    void computeImportCreatesCorrectiveActionForFailedFollowUpSamples() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-08-05"),
                "5 CFU.mL",
                new BigDecimal("5"),
                "CFU.mL"
            ),
            List.of(sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "15 CFU.mL",
                new BigDecimal("15"),
                "CFU.mL",
                List.of(),
                List.of(correctiveAction("Disinfect and retest"))
            )),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentAndPrimaryValue(workbookComment, new BigDecimal("5"), "5", "F5"),
            measurementBounds()
        );

        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Disinfect and retest")
        ));
    }

    @Test
    void computeImportCreatesCorrectiveActionForLaterMonthFailedFollowUps() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-08-05"),
                "10 CFU.mL",
                new BigDecimal("10"),
                "CFU.mL"
            ),
            List.of(sample(
                LocalDate.parse("2025-09-15"),
                LocalDate.parse("2025-09-20"),
                "15 CFU.mL",
                new BigDecimal("15"),
                "CFU.mL",
                List.of(),
                List.of(correctiveAction("Disinfect and retest"))
            )),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentCell(workbookComment, "F5"),
            measurementBounds()
        );

        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-09-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Disinfect and retest")
        ));
    }

    @Test
    void computeImportCreatesSyntheticCorrectiveActionsForStandaloneWorksheetFailuresWithoutComments() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        5,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "11",
                                new BigDecimal("11"),
                                null,
                                "F5"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(1, result.correctiveActions().size());
        assertEquals(LocalDate.parse("2025-08-01"), result.correctiveActions().getFirst().observedDate());
        assertTrue(result.correctiveActions().getFirst().description().contains(
            "out-of-spec worksheet sample without cell comment metadata"
        ));
    }

    @Test
    void computeImportDoesNotCreateCorrectiveActionsForCompliantCommentCells() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        5,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "9",
                                new BigDecimal("9"),
                                "Routine note only",
                                "F5"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(1, result.observations().size());
        assertTrue(result.observations().getFirst().compliant());
        assertEquals(0, result.correctiveActions().size());
    }

    @Test
    void computeImportMarksOnlyOutOfSpecCommentedCellsAsNonCompliant() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        5,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "9",
                                new BigDecimal("9"),
                                "Routine note only",
                                "F5"
                            ),
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "Endotoxin",
                                LocalDate.parse("2025-08-01"),
                                "2",
                                new BigDecimal("2"),
                                "Investigating elevated reading",
                                "I5"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(2, result.analyzedSamples().size());
        assertEquals(1L, result.analyzedSamples().stream().filter(
            LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming
        ).count());
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            "HPC".equals(sample.measurementName()) && sample.compliant()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            "Endotoxin".equals(sample.measurementName()) && sample.nonConforming()
        ));
    }

    @Test
    void computeImportUsesWorkbookMetricNamesWhenMeasurementBoundsUseDifferentFormatting() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F5");

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(
                measurementBound(1L, " hpc ", null, null, null, null, null, null, null, new BigDecimal("10")),
                measurementBound(2L, "ENDOTOXIN", null, null, null, null, null, null, null, new BigDecimal("1"))
            )
        );

        Map<String, Object> waterQualityHpcTrace = result.graphs().getFirst().data().getFirst();
        assertEquals("HPC", waterQualityHpcTrace.get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityHpcTrace.get("x"));
        assertEquals(List.of(1L), waterQualityHpcTrace.get("y"));
        assertTrue(result.observations().stream().anyMatch(observation -> "HPC".equals(observation.measurementName())));
    }

    @Test
    void computeImportAcceptsWorkbookStyleRetestCommentWhenWorksheetCarriesOriginalFailedResult() {
        ConfiguredLocationDashboardImportStrategy strategy = criticalPhStrategy();

        String commentText = workbookStyleComment(
            "First Test: 7.7",
            "Corrective Action Taken on: 8/2/25",
            "Drain tank, install new DI bottles",
            "Retest Sample Date: 8/3/25",
            "Result Date: 8/5/25",
            "Retest Result: 6.8"
        );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    38,
                    "Newport Beach",
                    "Hospital",
                    "Critical",
                    "DI Supply",
                    "Source to SPD",
                    List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                        "pH",
                        LocalDate.parse("2025-08-01"),
                        "7.7",
                        new BigDecimal("7.6"),
                        commentText,
                        "CB38"
                    ))
                ))
            ),
            List.of(measurementBound(1L, "pH", new BigDecimal("5.0"), new BigDecimal("7.5"), null, null, null, null, null, null))
        );

        assertEquals(1, result.correctiveActions().size());
        assertEquals(2, result.observations().size());
        assertTrue(result.observations().stream().anyMatch(observation ->
            LocalDate.parse("2025-08-03").equals(observation.observedDate()) && observation.compliant()
        ));
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-01").equals(draft.observedDate())
                && "pH".equals(draft.measurementName())
        ));
    }

    @Test
    void computeImportDeduplicatesWorkbookCommentPrimarySampleWhenItMatchesTheWorksheetSample() {
        ConfiguredLocationDashboardImportStrategy strategy = criticalPhStrategy();

        String workbookComment = workbookStyleComment(
            "First Test: 7.6",
            "Corrective Action: Drain tank, install new DI bottles",
            "Second Test: 6.8"
        );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    39,
                    "Newport Beach",
                    "Hospital",
                    "Critical",
                    "DI Return",
                    "Return from SPD",
                    List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                        "pH",
                        LocalDate.parse("2026-01-01"),
                        "7.6",
                        new BigDecimal("7.6"),
                        workbookComment,
                        "CB39"
                    ))
                ))
            ),
            List.of(measurementBound(1L, "pH", new BigDecimal("5.0"), new BigDecimal("7.5"), null, null, null, null, null, null))
        );

        assertEquals(1, result.correctiveActions().size());
        assertEquals(1, result.observations().size());
    }

    @Test
    void rangeProfileTreatsMaximumAsInclusive() {
        MeasurementBound measurementBound =
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10"));

        assertTrue(measurementBound.isCompliant(new BigDecimal("9.9")));
        assertTrue(measurementBound.isCompliant(new BigDecimal("10")));
    }

    @Test
    void rangeProfileTreatsZeroMaximumAsCompliant() {
        MeasurementBound measurementBound =
            measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO);

        assertTrue(measurementBound.isCompliant(BigDecimal.ZERO));
        assertFalse(measurementBound.isCompliant(new BigDecimal("0.1")));
    }

    @Test
    void computeImportCountsSingleConformantCommentedLegionellaSampleAsOneObservation() {
        ConfiguredLocationDashboardImportStrategy strategy = legionellaStrategy();

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            legionellaWorkbookWithComment("Routine note only", BigDecimal.ZERO, "0", "F5"),
            List.of(measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO))
        );

        assertEquals(1, result.observations().size());
        assertEquals(1, result.analyzedSamples().size());
        assertTrue(result.observations().getFirst().compliant());
        assertEquals("Legionella", result.observations().getFirst().measurementName());
    }

    @Test
    void computeImportDeduplicatesCommentFollowUpWhenNextWorksheetMonthRepeatsTheSameSample() {
        ConfiguredLocationDashboardImportStrategy strategy = legionellaStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Rm. 6127 POD DHW",
            sample(
                LocalDate.parse("2025-04-14"),
                LocalDate.parse("2025-04-22"),
                "1.6 CFU.mL",
                new BigDecimal("1.6"),
                "CFU.mL"
            ),
            List.of(
                sample(
                    LocalDate.parse("2025-04-23"),
                    LocalDate.parse("2025-05-01"),
                    "13 CFU.mL",
                    new BigDecimal("13"),
                    "CFU.mL"
                ),
                sample(
                    LocalDate.parse("2025-05-06"),
                    LocalDate.parse("2025-05-16"),
                    "ND",
                    BigDecimal.ZERO,
                    null
                )
            ),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            legionellaWorkbookWithFutureWorksheetValue(
                workbookComment,
                new BigDecimal("1.6"),
                "1.6",
                LocalDate.parse("2025-04-01"),
                "F5",
                new BigDecimal("13"),
                "13",
                LocalDate.parse("2025-05-01"),
                "G5"
            ),
            List.of(measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO))
        );

        assertEquals(3, result.observations().size());
        assertEquals(3, result.analyzedSamples().size());
        assertEquals(1, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                && LocalDate.parse("2025-04-14").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                && LocalDate.parse("2025-04-23").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                && LocalDate.parse("2025-05-06").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.compliant()
        ));
        assertFalse(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                && LocalDate.parse("2025-05-01").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
        ));
    }

    @Test
    void computeImportKeepsCommentFollowUpWhenLaterWorksheetMonthFallsOutsideDuplicateMargin() {
        ConfiguredLocationDashboardImportStrategy strategy = legionellaStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Rm. 6127 POD DHW",
            sample(
                LocalDate.parse("2025-04-14"),
                LocalDate.parse("2025-04-22"),
                "1.6 CFU.mL",
                new BigDecimal("1.6"),
                "CFU.mL"
            ),
            List.of(
                sample(
                    LocalDate.parse("2025-04-23"),
                    LocalDate.parse("2025-05-01"),
                    "13 CFU.mL",
                    new BigDecimal("13"),
                    "CFU.mL"
                ),
                sample(
                    LocalDate.parse("2025-05-06"),
                    LocalDate.parse("2025-05-16"),
                    "ND",
                    BigDecimal.ZERO,
                    null
                )
            ),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            legionellaWorkbookWithFutureWorksheetValue(
                workbookComment,
                new BigDecimal("1.6"),
                "1.6",
                LocalDate.parse("2025-04-01"),
                "F5",
                new BigDecimal("13"),
                "13",
                LocalDate.parse("2025-06-01"),
                "G5"
            ),
            List.of(measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO))
        );

        assertEquals(4, result.observations().size());
        assertEquals(4, result.analyzedSamples().size());
        assertEquals(2, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                && LocalDate.parse("2025-04-23").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                && LocalDate.parse("2025-06-01").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
    }

    @Test
    void computeImportDoesNotDeduplicateFollowUpFromSampledDateAloneWhenResultReceivedDateFallsOutsideMargin() {
        ConfiguredLocationDashboardImportStrategy strategy = legionellaStrategy();

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Rm. 6127 POD DHW",
            sample(
                LocalDate.parse("2025-04-14"),
                LocalDate.parse("2025-04-22"),
                "1.6 CFU.mL",
                new BigDecimal("1.6"),
                "CFU.mL"
            ),
            List.of(sample(
                LocalDate.parse("2025-04-23"),
                LocalDate.parse("2025-05-20"),
                "13 CFU.mL",
                new BigDecimal("13"),
                "CFU.mL"
            )),
            List.of(),
            List.of()
        ));

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            legionellaWorkbookWithFutureWorksheetValue(
                workbookComment,
                new BigDecimal("1.6"),
                "1.6",
                LocalDate.parse("2025-04-01"),
                "F5",
                new BigDecimal("13"),
                "13",
                LocalDate.parse("2025-05-01"),
                "G5"
            ),
            List.of(measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO))
        );

        assertEquals(3, result.observations().size());
        assertEquals(3, result.analyzedSamples().size());
        assertEquals(2, result.correctiveActions().size());
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL
                && LocalDate.parse("2025-04-23").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
        assertTrue(result.analyzedSamples().stream().anyMatch(sample ->
            sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
                && LocalDate.parse("2025-05-01").equals(sample.observedDate())
                && "Legionella".equals(sample.measurementName())
                && sample.nonConforming()
        ));
    }

    @Test
    void computeImportKeepsSyntheticCorrectiveActionIdentityStableWhenCommentCellReferenceChanges() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        List<String> originalTitles = strategy.computeImport(
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F5"),
            measurementBounds()
        ).correctiveActions().stream().map(LocationDashboardImportStrategy.CorrectiveActionDraft::title).toList();

        List<String> shiftedTitles = strategy.computeImport(
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F6"),
            measurementBounds()
        ).correctiveActions().stream().map(LocationDashboardImportStrategy.CorrectiveActionDraft::title).toList();

        assertEquals(originalTitles, shiftedTitles);
    }

    @Test
    void computeImportAssignsUnitsFromMeasurementConfig() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentAndPrimaryValue("Routine note only", new BigDecimal("8"), "8", "F5"),
            measurementBounds()
        );

        assertEquals("CFU/mL", result.analyzedSamples().stream()
            .filter(sample -> "HPC".equals(sample.measurementName()))
            .findFirst()
            .orElseThrow()
            .units());
    }

    @Test
    void computeImportResolvesHoagWorkbookBuildingAliasesAndDirectSystems() {
        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "irvine",
                        "Irvine",
                        List.of("Irvine"),
                        List.of(),
                        true
                    ),
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "16405-irvine",
                        "16405 Irvine",
                        List.of("Irvine"),
                        List.of("16405", "16105", "SPD-16405"),
                        false
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "utility-spd",
                        "Utility SPD",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of()
                    ),
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "utility-domestic-hot",
                        "Utility Domestic Hot",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of()
                    ),
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "steam",
                        "Steam",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of()
                    )
                ),
                List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                    "16405-irvine-water-quality",
                    "Water Quality Conformance",
                    "16405 Irvine",
                    LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                    "16405-irvine",
                    List.of("HPC"),
                    Map.of("HPC", "#1f77b4"),
                    "scatter"
                )),
                List.of(),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeAliasConfig(
                        "Utility SPD",
                        List.of("Utility HLD", "Utility Water")
                    ),
                    new LocationDashboardImportStrategyConfig.SystemTypeAliasConfig(
                        "Utility Domestic Hot",
                        List.of("Utility Main Hot 120F")
                    )
                )
            )
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        68,
                        "Irvine",
                        "SPD-16405",
                        "Utility- HLD",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "4",
                                new BigDecimal("4"),
                                null,
                                "K68"
                            )
                        )
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        73,
                        null,
                        "16105",
                        "Utility- Main Hot 120F",
                        "DHWR",
                        "Feeds Storage Tank",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "5",
                                new BigDecimal("5"),
                                null,
                                "K73"
                            )
                        )
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        74,
                        null,
                        "16105",
                        "Steam",
                        "DHWR",
                        "Direct system fallback",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "6",
                                new BigDecimal("6"),
                                null,
                                "K74"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(measurementBound(1L, "HPC", null, null, null, new BigDecimal("500"), null, null, null, null))
        );

        assertEquals(1, result.graphs().size());
        assertEquals("16405-irvine-water-quality", result.graphs().getFirst().graphId());
        assertEquals(List.of(0L), result.graphs().getFirst().data().getFirst().get("y"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) result.graphs().getFirst().data().getFirst().get("customdata");
        assertEquals(3L, ((Number) customData.getFirst().get("sampleCount")).longValue());
        assertEquals(3, result.observations().size());
        assertEquals("16405 Irvine", result.observations().getFirst().facilityName());
        assertEquals("Utility SPD", result.observations().getFirst().systemTypeName());
        assertEquals("16405 Irvine", result.observations().get(1).facilityName());
        assertEquals("Utility Domestic Hot", result.observations().get(1).systemTypeName());
        assertEquals("Steam", result.observations().get(2).systemTypeName());
        assertEquals(0, result.correctiveActions().size());
    }

    @Test
    void computeImportResolvesHoagSublocationWhenBuildingAliasAppearsInFacilityColumn() {
        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "irvine",
                        "Irvine",
                        List.of("Irvine"),
                        List.of(),
                        true
                    ),
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "16405-irvine",
                        "16405 Irvine",
                        List.of("Irvine"),
                        List.of("16405", "16105", "SPD-16405"),
                        false
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "utility-spd",
                        "Utility SPD",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of()
                    )
                ),
                List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                    "16405-irvine-water-quality",
                    "Water Quality Conformance",
                    "16405 Irvine",
                    LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                    "16405-irvine",
                    List.of("HPC"),
                    Map.of("HPC", "#1f77b4"),
                    "scatter"
                )),
                List.of(),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeAliasConfig(
                        "Utility SPD",
                        List.of("Utility HLD", "Utility Water")
                    )
                )
            )
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        68,
                        "SPD-16405",
                        null,
                        "Utility- HLD",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "4",
                                new BigDecimal("4"),
                                null,
                                "K68"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(measurementBound(1L, "HPC", null, null, null, new BigDecimal("500"), null, null, null, null))
        );

        assertEquals(1, result.graphs().size());
        assertEquals("16405-irvine-water-quality", result.graphs().getFirst().graphId());
        assertEquals(1, result.observations().size());
        assertEquals("16405 Irvine", result.observations().getFirst().facilityName());
        assertEquals("Utility SPD", result.observations().getFirst().systemTypeName());
        assertEquals(0, result.correctiveActions().size());
    }

    @Test
    void computeImportSkipsUnrecognizedWorkbookValuesInsteadOfReusingPreviousSystemType() {
        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                        List.of("Cooling Towers")
                    )
                ),
                List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                    "newport-beach-system-type",
                    "System Type Conformance",
                    "Newport Beach",
                    LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                    "newport-beach",
                    List.of("Cooling Towers"),
                    Map.of("Cooling Towers", "#1f77b4"),
                    "scatter"
                )),
                List.of(),
                List.of()
            )
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        68,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "4",
                                new BigDecimal("4"),
                                null,
                                "K68"
                            )
                        )
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        69,
                        null,
                        null,
                        "Unknown System Type",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "5",
                                new BigDecimal("5"),
                                null,
                                "K69"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("500")))
        );

        assertEquals(1, result.observations().size());
        assertEquals("Cooling Towers", result.observations().getFirst().systemTypeName());
        assertEquals(1, result.graphs().getFirst().data().size());
        assertEquals("Cooling Towers", result.graphs().getFirst().data().getFirst().get("name"));
    }

    @Test
    void computeImportCoercesHoagSystemTypeAliasesIntoCanonicalDisplayNames() {
        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "16405-irvine",
                        "16405 Irvine",
                        List.of("Irvine"),
                        List.of("16405", "16105", "SPD-16405"),
                        false
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "utility-spd",
                        "Utility SPD",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of()
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "16405-irvine-water-quality",
                        "Water Quality Conformance",
                        "16405 Irvine",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "16405-irvine",
                        List.of("HPC"),
                        Map.of("HPC", "#1f77b4"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "16405-irvine-system-type",
                        "System Type Conformance",
                        "16405 Irvine",
                        LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                        "16405-irvine",
                        List.of("Utility SPD"),
                        Map.of(
                            "Utility SPD", "#1f77b4"
                        ),
                        "scatter"
                    )
                ),
                List.of(),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeAliasConfig(
                        "Utility SPD",
                        List.of("Utility HLD", "Utility", "Utility SPD", "Utility Water SPD", "Utility Soft", "Utility Water")
                    )
                )
            )
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        68,
                        "Irvine",
                        "SPD-16405",
                        "Utility Water SPD",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "4",
                                new BigDecimal("4"),
                                null,
                                "K68"
                            )
                        )
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        69,
                        null,
                        "16105",
                        "Utility Water",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "5",
                                new BigDecimal("5"),
                                null,
                                "K69"
                            )
                        )
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        70,
                        null,
                        "16105",
                        "Utility-Soft",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "6",
                                new BigDecimal("6"),
                                null,
                                "K70"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(measurementBound(1L, "HPC", null, null, null, new BigDecimal("500"), null, new BigDecimal("500"), null, null))
        );

        assertEquals(2, result.graphs().size());
        assertEquals(3, result.observations().size());
        assertEquals("Utility SPD", result.observations().getFirst().systemTypeName());
        assertEquals("Utility SPD", result.observations().get(1).systemTypeName());
        assertEquals("Utility SPD", result.observations().get(2).systemTypeName());

        Map<String, Object> waterQualityTrace = result.graphs().getFirst().data().getFirst();
        assertEquals("HPC", waterQualityTrace.get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityTrace.get("x"));
        assertEquals(List.of(0L), waterQualityTrace.get("y"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) waterQualityTrace.get("customdata");
        assertEquals(3L, ((Number) customData.getFirst().get("sampleCount")).longValue());

        Map<String, Object> systemTypeTrace = result.graphs().get(1).data().getFirst();
        assertEquals("Utility SPD", systemTypeTrace.get("name"));
        assertEquals(List.of("2025-08-01"), systemTypeTrace.get("x"));
        assertEquals(List.of(0L), systemTypeTrace.get("y"));
    }

    @Test
    void computeImportResolvesSublocationDisplayNamesWithoutRequiringBuildingAliases() {
        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "surgical-pavilion",
                        "Surgical Pavilion",
                        List.of("Irvine"),
                        List.of("Surgical Pavilion"),
                        false
                    )
                ),
                List.of(
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                        List.of("Cooling Towers")
                    )
                ),
                List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                    "surgical-pavilion-water-quality",
                    "Water Quality Conformance",
                    "Surgical Pavilion",
                    LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                    "surgical-pavilion",
                    List.of("HPC"),
                    Map.of("HPC", "#1f77b4"),
                    "scatter"
                )),
                List.of(),
                List.of()
            )
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Hoag Hospital",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        68,
                        "Surgical Pavilion",
                        null,
                        "Cooling Towers",
                        "DI Source",
                        "Source to SPD",
                        List.of(
                            new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                                "HPC",
                                LocalDate.parse("2025-08-01"),
                                "4",
                                new BigDecimal("4"),
                                null,
                                "K68"
                            )
                        )
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            List.of(measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("500")))
        );

        assertEquals(1, result.observations().size());
        assertEquals("Surgical Pavilion", result.observations().getFirst().facilityName());
        assertEquals(1, result.graphs().size());
        assertEquals("HPC", result.graphs().getFirst().data().getFirst().get("name"));
    }

    @Test
    void computeImportCarriesForwardBuildingAcrossContinuationRowsForResolutionAnalysis() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
                "Newport Beach",
                List.of(
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        5,
                        "Newport Beach",
                        "Hospital",
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "15",
                            new BigDecimal("15"),
                            null,
                            "F5"
                        ))
                    ),
                    new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                        6,
                        null,
                        null,
                        "Cooling Towers",
                        "Recirc Line",
                        "CTI/514P",
                        List.of(new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-05"),
                            "5",
                            new BigDecimal("5"),
                            null,
                            "G5"
                        ))
                    )
                )
            );

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> nonConformingSamples = result.analyzedSamples().stream()
            .filter(LocationDashboardImportStrategy.AnalyzedSamplePoint::nonConforming)
            .toList();

        assertEquals(1, nonConformingSamples.size());
        assertTrue(nonConformingSamples.getFirst().resolved());
        assertEquals(4L, nonConformingSamples.getFirst().turnaroundDays());
    }

    @Test
    void constructorRejectsMissingSystemRangeProfile() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new ConfiguredLocationDashboardImportStrategy(
                new LocationDashboardImportStrategyConfig(
                    "Newport Beach",
                    List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    )),
                    List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        null,
                        List.of("Cooling Towers")
                    )),
                    List.of(validWaterQualityGraphConfig()),
                    List.of(),
                    List.of()
                )
            )
        );

        assertTrue(error.getMessage().contains("range profile"));
    }

    @Test
    void constructorRejectsMissingGraphImportType() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new ConfiguredLocationDashboardImportStrategy(
                new LocationDashboardImportStrategyConfig(
                    "Newport Beach",
                    List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    )),
                    List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                        List.of("Cooling Towers")
                    )),
                    List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                        "water-quality",
                        "Water Quality Conformance",
                        "Newport Beach",
                        null,
                        "newport-beach",
                        List.of("HPC", "Endotoxin"),
                        Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                        "scatter"
                    )),
                    List.of(),
                    List.of()
                )
            )
        );

        assertTrue(error.getMessage().contains("import type"));
    }

    @Test
    void constructorRejectsDuplicateGraphNameTitleCombination() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new ConfiguredLocationDashboardImportStrategy(
                new LocationDashboardImportStrategyConfig(
                    "Newport Beach",
                    List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    )),
                    List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                        List.of("Cooling Towers")
                    )),
                    List.of(
                        validWaterQualityGraphConfig(),
                        new LocationDashboardImportStrategyConfig.GraphConfig(
                            "water-quality-duplicate",
                            "Water Quality Conformance",
                            "Newport Beach",
                            LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                            "newport-beach",
                            List.of("HPC", "Endotoxin"),
                            Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                            "scatter"
                        )
                    ),
                    List.of(),
                    List.of()
                )
            )
        );

        assertTrue(error.getMessage().contains("name/title combinations"));
    }

    @Test
    void constructorRejectsImportedAndDerivedGraphNameTitleCollision() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new ConfiguredLocationDashboardImportStrategy(
                new LocationDashboardImportStrategyConfig(
                    "Newport Beach",
                    List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    )),
                    List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "cooling-towers",
                        "Cooling Towers",
                        LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                        List.of("Cooling Towers")
                    )),
                    List.of(validWaterQualityGraphConfig()),
                    List.of(new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                        "water-quality-summary",
                        "Water Quality Conformance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES,
                        "pie"
                    )),
                    List.of()
                )
            )
        );

        assertTrue(error.getMessage().contains("across imported and derived graphs"));
    }

    private ConfiguredLocationDashboardImportStrategy buildStrategy() {
        return new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Newport Beach",
                List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                    "newport-beach",
                    "Newport Beach",
                    List.of("Newport Beach"),
                    List.of(),
                    true
                )),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                    "cooling-towers",
                    "Cooling Towers",
                    LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                    List.of("Cooling Towers")
                )),
                List.of(
                    validWaterQualityGraphConfig(),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "system-type",
                        "System Type Conformance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                        "newport-beach",
                        List.of("Cooling Towers"),
                        Map.of("Cooling Towers", "#d62728"),
                        "scatter"
                    )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(new LocationDashboardImportStrategyConfig.MeasurementUnitConfig(
                    "CFU/mL",
                    List.of(),
                    List.of("HPC", "Endotoxin")
                ))
            )
        );
    }

    private ConfiguredLocationDashboardImportStrategy criticalPhStrategy() {
        return new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Newport Beach",
                List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                    "newport-beach",
                    "Newport Beach",
                    List.of("Newport Beach"),
                    List.of(),
                    true
                )),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                    "critical-spd",
                    "Critical SPD",
                    LocationDashboardImportStrategyConfig.RangeProfile.CRITICAL,
                    List.of()
                )),
                List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                    "water-quality",
                    "Water Quality Conformance",
                    "Newport Beach",
                    LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                    "newport-beach",
                    List.of("pH"),
                    Map.of("pH", "#ff7f0e"),
                    "scatter"
                )),
                List.of(),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeAliasConfig(
                    "Critical SPD",
                    List.of("Critical")
                ))
            )
        );
    }

    private ConfiguredLocationDashboardImportStrategy legionellaStrategy() {
        return new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Newport Beach",
                List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                    "newport-beach",
                    "Newport Beach",
                    List.of("Newport Beach"),
                    List.of(),
                    true
                )),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                    "cooling-towers",
                    "Cooling Towers",
                    LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                    List.of("Cooling Towers")
                )),
                List.of(
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "water-quality",
                        "Water Quality Conformance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "newport-beach",
                        List.of("Legionella"),
                        Map.of("Legionella", "#1f77b4"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "system-type",
                        "System Type Conformance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                        "newport-beach",
                        List.of("Cooling Towers"),
                        Map.of("Cooling Towers", "#d62728"),
                        "scatter"
                    )
                ),
                List.of(),
                List.of()
            )
        );
    }

    private LocationDashboardImportStrategyConfig.GraphConfig validWaterQualityGraphConfig() {
        return new LocationDashboardImportStrategyConfig.GraphConfig(
            "water-quality",
            "Water Quality Conformance",
            "Newport Beach",
            LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
            "newport-beach",
            List.of("HPC", "Endotoxin"),
            Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
            "scatter"
        );
    }

    private List<MeasurementBound> measurementBounds() {
        return List.of(
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10")),
            measurementBound(2L, "Endotoxin", null, null, null, null, null, null, null, new BigDecimal("1"))
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbookWithCommentCell(
        String commentText,
        String firstCellReference
    ) {
        return workbookWithCommentAndPrimaryValue(commentText, new BigDecimal("10"), "10", firstCellReference);
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbookWithCommentAndPrimaryValue(
        String commentText,
        BigDecimal primaryNumericValue,
        String primaryRawValue,
        String firstCellReference
    ) {
        // Keep the workbook comment text verbatim so the tests can cover the
        // semicolon-delimited and multi-line workbook comment formats directly.
        return new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
            "Newport Beach",
            List.of(
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    5,
                    "Newport Beach",
                    "Hospital",
                    "Cooling Towers",
                    "Recirc Line",
                    "CTI/514P",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            primaryRawValue,
                            primaryNumericValue,
                            commentText,
                            firstCellReference
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Endotoxin",
                            LocalDate.parse("2025-08-01"),
                            "0",
                            new BigDecimal("0"),
                            null,
                            "I5"
                        )
                    )
                ),
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    6,
                    null,
                    null,
                    null,
                    "Recirc Line",
                    "CTI/514P",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "11",
                            new BigDecimal("11"),
                            null,
                            "F6"
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Endotoxin",
                            LocalDate.parse("2025-08-01"),
                            "2",
                            new BigDecimal("2"),
                            null,
                            "I6"
                        )
                    )
                )
            )
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook legionellaWorkbookWithComment(
        String commentText,
        BigDecimal numericValue,
        String rawValue,
        String cellReference
    ) {
        return new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
            "Newport Beach",
            List.of(
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    5,
                    "Newport Beach",
                    "Hospital",
                    "Cooling Towers",
                    "Recirc Line",
                    "CTI/514P",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Legionella",
                            LocalDate.parse("2025-08-01"),
                            rawValue,
                            numericValue,
                            commentText,
                            cellReference
                        )
                    )
                )
            )
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook legionellaWorkbookWithFutureWorksheetValue(
        String commentText,
        BigDecimal primaryNumericValue,
        String primaryRawValue,
        LocalDate primaryObservedDate,
        String primaryCellReference,
        BigDecimal futureNumericValue,
        String futureRawValue,
        LocalDate futureObservedDate,
        String futureCellReference
    ) {
        return new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
            "Newport Beach",
            List.of(
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    5,
                    "Newport Beach",
                    "Hospital",
                    "Cooling Towers",
                    "Rm. 6127 POD DHW",
                    "Fixture sample",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Legionella",
                            primaryObservedDate,
                            primaryRawValue,
                            primaryNumericValue,
                            commentText,
                            primaryCellReference
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Legionella",
                            futureObservedDate,
                            futureRawValue,
                            futureNumericValue,
                            null,
                            futureCellReference
                        )
                    )
                )
            )
        );
    }

    private MeasurementBound measurementBound(
        Long id,
        String name,
        BigDecimal criticalMin,
        BigDecimal criticalMax,
        BigDecimal utilityMin,
        BigDecimal utilityMax,
        BigDecimal potableMin,
        BigDecimal potableMax,
        BigDecimal towersMin,
        BigDecimal towersMax
    ) {
        MeasurementBound measurementBound = new MeasurementBound();
        measurementBound.setId(id);
        measurementBound.setMeasurementName(name);
        if (criticalMin != null || criticalMax != null) {
            measurementBound.setType("critical");
            measurementBound.setMin(criticalMin);
            measurementBound.setMax(criticalMax);
        } else if (utilityMin != null || utilityMax != null) {
            measurementBound.setType("utility");
            measurementBound.setMin(utilityMin);
            measurementBound.setMax(utilityMax);
        } else if (potableMin != null || potableMax != null) {
            measurementBound.setType("potable");
            measurementBound.setMin(potableMin);
            measurementBound.setMax(potableMax);
        } else {
            measurementBound.setType("towers");
            measurementBound.setMin(towersMin);
            measurementBound.setMax(towersMax);
        }
        return measurementBound;
    }
}
