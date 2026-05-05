package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardImportStrategyRegistryTest {
    @Test
    void resolveLoadsConfiguredClasspathStrategiesByNormalizedLocationName() {
        LocationDashboardImportStrategyRegistry registry = new LocationDashboardImportStrategyRegistry();

        LocationDashboardImportStrategy strategy = registry.resolve("  newport    beach ")
            .orElseThrow(() -> new AssertionError("Expected Newport Beach dashboard strategy to load"));

        assertEquals("Newport Beach", strategy.locationName());
        assertEquals(2, strategy.graphDefinitions().size());
        assertEquals("newport-beach-water-quality-compliance", strategy.graphDefinitions().getFirst().id());
        assertTrue(
            strategy.graphDefinitions().stream()
                .anyMatch(graph -> graph.importType() == LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE)
        );
    }
}
