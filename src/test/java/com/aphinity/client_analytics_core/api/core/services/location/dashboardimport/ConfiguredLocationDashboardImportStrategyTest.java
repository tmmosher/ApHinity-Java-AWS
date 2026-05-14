package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> waterQualityHpcTrace = result.graphs().getFirst().data().getFirst();
        assertEquals("HPC", waterQualityHpcTrace.get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityHpcTrace.get("x"));
        assertEquals(List.of(0.0d), waterQualityHpcTrace.get("y"));

        Map<String, Object> systemTypeTrace = result.graphs().get(1).data().getFirst();
        assertEquals("Cooling Towers", systemTypeTrace.get("name"));
        assertEquals(List.of(25.0d), systemTypeTrace.get("y"));

        assertEquals(4, result.observations().size());
        assertEquals(1, result.observations().stream().filter(observation -> observation.compliant()).count());
        assertEquals(3, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            draft.title().startsWith("CA: HPC 2025-08-01")
                && draft.description().contains("CA: Drain Tank, install new DI bottles")
        ));
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            "Endotoxin".equals(draft.measurementName())
                && draft.description().contains("out-of-spec worksheet sample without cell comment metadata")
        ));
    }

    @Test
    void computeImportReadsStructuredCommentSamplesAsSupplementalObservations() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL",
                "notes": ["30 sec. flush with sample port wipe down."],
                "correctiveActions": [
                  {
                    "text": "New sample port installed"
                  }
                ]
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-08-15",
                  "resultReceivedOn": "2025-08-20",
                  "resultRaw": "5 CFU.mL",
                  "resultValue": 5,
                  "resultUnit": "CFU.mL",
                  "correctiveActions": [
                    {
                      "text": "Flush and retest"
                    }
                  ]
                }
              ],
              "correctiveActions": [
                {
                  "text": "External note"
                }
              ],
              "notes": [
                "General comment note"
              ]
            }
            """;

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell(structuredComment, "F5");

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
            draft.description().contains("Primary Sample: sampled on 2025-08-01")
                && draft.description().contains("Sample Location: Cooling Tower Sample Port")
                && draft.description().contains("CA: External note")
        ));
    }

    @Test
    void computeImportCreatesCorrectiveActionsForOutOfSpecStructuredCommentSamples() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL"
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-08-15",
                  "resultReceivedOn": "2025-08-20",
                  "resultRaw": "15 CFU.mL",
                  "resultValue": 15,
                  "resultUnit": "CFU.mL",
                  "correctiveActions": [
                    {
                      "text": "Disinfect and retest"
                    }
                  ]
                }
              ]
            }
            """;

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbookWithCommentCell(structuredComment, "F5");

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbook,
            measurementBounds()
        );

        assertEquals(4, result.correctiveActions().size());
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "Newport Beach".equals(draft.facilityName())
                && "Cooling Towers".equals(draft.systemTypeName())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Supplemental Sample 1: sampled on 2025-08-15")
                && draft.description().contains("Supplemental Sample 1 CA: Disinfect and retest")
        ));
    }

    @Test
    void computeImportDoesNotDuplicatePrimaryStructuredCommentSamplesWhenTheyUseActualSampleDatesInsideTheWorksheetMonth() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-15",
                "resultReceivedOn": "2025-08-20",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL"
              }
            }
            """;

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentCell(structuredComment, "F5"),
            measurementBounds()
        );

        assertEquals(4, result.observations().size());
        assertEquals(3, result.correctiveActions().size());
        assertFalse(result.observations().stream().anyMatch(observation ->
            LocalDate.parse("2025-08-15").equals(observation.observedDate())
                && "HPC".equals(observation.measurementName())
        ));
        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-01").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Primary Sample: sampled on 2025-08-15")
        ));
        assertFalse(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
        ));
    }

    @Test
    void computeImportCreatesCorrectiveActionForFailedFollowUpSamplesWhenTheWorksheetSampleWasCompliant() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "5 CFU.mL",
                "resultValue": 5,
                "resultUnit": "CFU.mL"
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-08-15",
                  "resultReceivedOn": "2025-08-20",
                  "resultRaw": "15 CFU.mL",
                  "resultValue": 15,
                  "resultUnit": "CFU.mL",
                  "correctiveActions": [
                    {
                      "text": "Disinfect and retest"
                    }
                  ]
                }
              ]
            }
            """;

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithStructuredCommentAndPrimaryValue(structuredComment, new BigDecimal("5"), "5", "F5"),
            measurementBounds()
        );

        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-08-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Supplemental Sample 1: sampled on 2025-08-15")
        ));
    }

    @Test
    void computeImportCreatesSeparateCorrectiveActionForLaterMonthFailedFollowUps() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL"
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-09-15",
                  "resultReceivedOn": "2025-09-20",
                  "resultRaw": "15 CFU.mL",
                  "resultValue": 15,
                  "resultUnit": "CFU.mL",
                  "correctiveActions": [
                    {
                      "text": "Disinfect and retest"
                    }
                  ]
                }
              ]
            }
            """;

        LocationDashboardImportStrategy.LocationDashboardImportComputation result = strategy.computeImport(
            workbookWithCommentCell(structuredComment, "F5"),
            measurementBounds()
        );

        assertTrue(result.correctiveActions().stream().anyMatch(draft ->
            LocalDate.parse("2025-09-15").equals(draft.observedDate())
                && "HPC".equals(draft.measurementName())
                && draft.description().contains("Supplemental Sample 1: sampled on 2025-09-15")
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
        assertEquals(List.of(0.0d), waterQualityHpcTrace.get("y"));
        assertTrue(result.observations().stream().anyMatch(observation -> "HPC".equals(observation.measurementName())));
    }

    @Test
    void rangeProfileTreatsMaximumAsExclusive() {
        MeasurementBound measurementBound =
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10"));

        assertTrue(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(new BigDecimal("9.9"), measurementBound));
        assertFalse(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(new BigDecimal("10"), measurementBound));
    }

    @Test
    void rangeProfileTreatsZeroMaximumAsCompliant() {
        MeasurementBound measurementBound =
            measurementBound(1L, "Legionella", null, null, null, null, null, null, null, BigDecimal.ZERO);

        assertTrue(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(BigDecimal.ZERO, measurementBound));
        assertFalse(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(new BigDecimal("0.1"), measurementBound));
    }

    @Test
    void computeImportKeepsCorrectiveActionIdentityStableWhenCellReferenceChanges() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String originalTitle = strategy.computeImport(
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst().title();

        String shiftedTitle = strategy.computeImport(
            workbookWithCommentCell("Test 1;350;CA;Drain Tank, install new DI bottles", "F6"),
            measurementBounds()
        ).correctiveActions().getFirst().title();

        assertEquals(originalTitle, shiftedTitle);
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
                    "Water Quality Compliance",
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
        assertEquals(List.of(100.0d), result.graphs().getFirst().data().getFirst().get("y"));
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
                    "System Type Compliance",
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
                        "Water Quality Compliance",
                        "16405 Irvine",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "16405-irvine",
                        List.of("HPC"),
                        Map.of("HPC", "#1f77b4"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "16405-irvine-system-type",
                        "System Type Compliance",
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
        assertEquals(List.of(100.0d), waterQualityTrace.get("y"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) waterQualityTrace.get("customdata");
        assertEquals(3L, ((Number) customData.getFirst().get("sampleCount")).longValue());

        Map<String, Object> systemTypeTrace = result.graphs().get(1).data().getFirst();
        assertEquals("Utility SPD", systemTypeTrace.get("name"));
        assertEquals(List.of("2025-08-01"), systemTypeTrace.get("x"));
        assertEquals(List.of(100.0d), systemTypeTrace.get("y"));
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
                    "Water Quality Compliance",
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
                        "Water Quality Compliance",
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
                            "Water Quality Compliance",
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
                        "Water Quality Compliance",
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
                        "System Type Compliance",
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
            "Water Quality Compliance",
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
        return workbookWithStructuredCommentAndPrimaryValue(commentText, new BigDecimal("10"), "10", firstCellReference);
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbookWithStructuredCommentAndPrimaryValue(
        String commentText,
        BigDecimal primaryNumericValue,
        String primaryRawValue,
        String firstCellReference
    ) {
        // Keep the workbook comment text verbatim so the tests can cover both
        // the legacy semicolon-delimited format and the new structured JSON payload.
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
        measurementBound.setCriticalRangeMin(criticalMin);
        measurementBound.setCriticalRangeMax(criticalMax);
        measurementBound.setUtilityRangeMin(utilityMin);
        measurementBound.setUtilityRangeMax(utilityMax);
        measurementBound.setPotableRangeMin(potableMin);
        measurementBound.setPotableRangeMax(potableMax);
        measurementBound.setTowersRangeMin(towersMin);
        measurementBound.setTowersRangeMax(towersMax);
        return measurementBound;
    }
}
