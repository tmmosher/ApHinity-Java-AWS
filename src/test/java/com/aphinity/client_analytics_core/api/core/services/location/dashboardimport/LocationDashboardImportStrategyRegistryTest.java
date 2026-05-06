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
        assertEquals(2, strategy.graphDefinitions().size());
        assertEquals("newport-beach-water-quality-compliance", strategy.graphDefinitions().getFirst().id());
        assertEquals(
            List.of("HPC", "Endotoxin", "Legionella", "pH", "Conductivity", "Alkalinity", "Hardness"),
            strategy.graphDefinitions().getFirst().traceOrder()
        );
        assertEquals(
            List.of("Utility-HLD", "Utility", "Steam", "Critical", "Utility-Hot", "Cooling Towers"),
            strategy.graphDefinitions().get(1).traceOrder()
        );
        assertTrue(
            strategy.graphDefinitions().stream()
                .anyMatch(graph -> graph.importType() == LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE)
        );
    }
}
