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
        assertEquals("newport-beach-water-quality-conformance", strategy.graphDefinitions().getFirst().id());
        assertEquals("Water Quality Conformance", strategy.graphDefinitions().getFirst().name());
        assertEquals("Newport Beach", strategy.graphDefinitions().getFirst().title());
        assertEquals(
            new LocationDashboardImportStrategyConfig.GraphAnchor(
                LocationDashboardImportStrategyConfig.GraphDimension.SUBLOCATION,
                "newport-beach"
            ),
            strategy.graphDefinitions().getFirst().effectiveAnchor()
        );
        assertEquals(
            LocationDashboardImportStrategyConfig.GraphDimension.MEASUREMENT,
            strategy.graphDefinitions().getFirst().effectiveTraceBy()
        );
        assertEquals(
            List.of("HPC", "Endotoxin", "Legionella", "pH", "Conductivity", "Alkalinity", "Hardness"),
            strategy.graphDefinitions().getFirst().traceOrder()
        );
        assertEquals(
            List.of("Utility SPD", "Steam", "Critical SPD", "Utility Domestic Hot", "Cooling Tower"),
            strategy.graphDefinitions().get(1).traceOrder()
        );
        assertEquals("irvine-water-quality-conformance", strategy.graphDefinitions().get(2).id());
        assertEquals("Irvine", strategy.graphDefinitions().get(2).title());
        assertEquals("surgical-pavilion-system-type-conformance", strategy.graphDefinitions().get(7).id());
        assertEquals(10, strategy.derivedGraphDefinitions().size());
        assertEquals("total-samples", strategy.derivedGraphDefinitions().getFirst().id());
        assertEquals("Total Number of Samples", strategy.derivedGraphDefinitions().getFirst().name());
        assertEquals("non-conformance-status-by-facility", strategy.derivedGraphDefinitions().get(8).id());
        assertTrue(
            strategy.graphDefinitions().stream()
                .anyMatch(graph -> graph.importType() == LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE)
        );
    }

    @Test
    void resolveUsesAppleDestinationHeadersAsIdentityKeys() {
        LocationDashboardImportStrategyRegistry registry = new LocationDashboardImportStrategyRegistry();

        LocationDashboardImportStrategy strategy = registry.resolve("apple inc.").orElseThrow();

        assertEquals("system", strategy.spreadsheetIdentityPattern().get(0).identityKey());
        assertEquals("system", strategy.spreadsheetIdentityPattern().get(0).column());
        assertEquals("site", strategy.spreadsheetIdentityPattern().get(1).identityKey());
        assertEquals("site", strategy.spreadsheetIdentityPattern().get(1).column());
        assertEquals(3, strategy.graphDefinitions().size());
        assertEquals(11, strategy.derivedGraphDefinitions().size());
        LocationDashboardImportStrategyConfig.DerivedGraphConfig sunburst = strategy.derivedGraphDefinitions().get(9);
        assertEquals("sample-conformance-hierarchy", sunburst.id());
        assertEquals(LocationDashboardImportStrategyConfig.DerivedGraphType.SAMPLE_CONFORMANCE_HIERARCHY,
            sunburst.derivedType());
        assertEquals(
            java.util.Arrays.asList("system", "site", null),
            sunburst.hierarchy().stream()
                .map(LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel::key)
                .toList()
        );
        assertEquals(
            new LocationDashboardImportStrategyConfig.GraphAnchor(
                LocationDashboardImportStrategyConfig.GraphDimension.SYSTEM,
                "towers"
            ),
            strategy.graphDefinitions().getFirst().effectiveAnchor()
        );
        assertEquals(
            LocationDashboardImportStrategyConfig.GraphDimension.SUBLOCATION,
            strategy.graphDefinitions().getFirst().effectiveTraceBy()
        );
    }
}
