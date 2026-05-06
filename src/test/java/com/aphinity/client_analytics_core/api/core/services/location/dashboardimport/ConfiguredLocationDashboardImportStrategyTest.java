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
            workbookWithCommentCell("Drain Tank, install new DI bottles", "F5");

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
        assertEquals(1, result.correctiveActions().size());
        assertTrue(result.correctiveActions().getFirst().title().startsWith("CA: HPC 2025-08-01"));
        assertTrue(result.correctiveActions().getFirst().description().contains("CA: Drain Tank, install new DI bottles"));
    }

    @Test
    void rangeProfileTreatsMaximumAsExclusive() {
        MeasurementBound measurementBound =
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10"));

        assertTrue(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(new BigDecimal("9.9"), measurementBound));
        assertFalse(LocationDashboardImportStrategyConfig.RangeProfile.TOWERS.isCompliant(new BigDecimal("10"), measurementBound));
    }

    @Test
    void computeImportKeepsCorrectiveActionIdentityStableWhenCellReferenceChanges() {
        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();

        String originalTitle = strategy.computeImport(
            workbookWithCommentCell("Drain Tank, install new DI bottles", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst().title();

        String shiftedTitle = strategy.computeImport(
            workbookWithCommentCell("Drain Tank, install new DI bottles", "F6"),
            measurementBounds()
        ).correctiveActions().getFirst().title();

        assertEquals(originalTitle, shiftedTitle);
    }

    @Test
    void computeImportResolvesHoagWorkbookBuildingAndSystemAliases() {
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
                        "utility-hld",
                        "Utility-HLD",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of("Utility- HLD")
                    ),
                    new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                        "utility-hot",
                        "Utility-Hot",
                        LocationDashboardImportStrategyConfig.RangeProfile.UTILITY,
                        List.of("Utility- Main Hot 120F")
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
                List.of()
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
        assertEquals(2, result.observations().size());
        assertEquals("16405 Irvine", result.observations().getFirst().facilityName());
        assertEquals("Utility-HLD", result.observations().getFirst().systemTypeName());
        assertEquals("16405 Irvine", result.observations().get(1).facilityName());
        assertEquals("Utility-Hot", result.observations().get(1).systemTypeName());
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
                    ))
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
        String correctiveActionDescription,
        String firstCellReference
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
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "10",
                            new BigDecimal("10"),
                            "Test 1;350;CA;" + correctiveActionDescription,
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
