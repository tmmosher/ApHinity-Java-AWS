package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardImportStrategyRegistryTest {
    @Test
    void resolveLoadsConfiguredClasspathStrategiesByNormalizedLocationName() {
        LocationDashboardImportStrategyRegistry registry = new LocationDashboardImportStrategyRegistry();

        LocationDashboardImportStrategy strategy = registry.resolve("  hoag    hospital ")
            .orElseThrow(() -> new AssertionError("Expected Hoag Hospital dashboard strategy to load"));

        assertEquals("Hoag Hospital", strategy.locationName());
        assertEquals(8, strategy.graphDefinitions().size());
        assertEquals("newport-beach-water-quality-compliance", strategy.graphDefinitions().getFirst().id());
        assertEquals("Water Quality Compliance", strategy.graphDefinitions().getFirst().name());
        assertEquals("Newport Beach", strategy.graphDefinitions().getFirst().title());
        assertEquals(
            List.of("HPC", "Endotoxin", "Legionella", "pH", "Conductivity", "Alkalinity", "Hardness"),
            strategy.graphDefinitions().getFirst().traceOrder()
        );
        assertEquals(
            List.of("Utility-HLD", "Utility", "Steam", "Critical", "Utility-Hot", "Cooling Towers"),
            strategy.graphDefinitions().get(1).traceOrder()
        );
        assertEquals("irvine-water-quality-compliance", strategy.graphDefinitions().get(2).id());
        assertEquals("Irvine", strategy.graphDefinitions().get(2).title());
        assertEquals("surgical-pavilion-system-type-compliance", strategy.graphDefinitions().get(7).id());
        assertEquals(9, strategy.derivedGraphDefinitions().size());
        assertEquals("total-samples", strategy.derivedGraphDefinitions().getFirst().id());
        assertEquals("Total Number of Samples", strategy.derivedGraphDefinitions().getFirst().name());
        assertEquals("non-conformance-status-by-facility", strategy.derivedGraphDefinitions().get(8).id());
        assertTrue(
            strategy.graphDefinitions().stream()
                .anyMatch(graph -> graph.importType() == LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE)
        );
    }
}
