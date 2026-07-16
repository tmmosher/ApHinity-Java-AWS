package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDashboardGraphMetadataSupportTest {
    @Test
    void derivedBarLayoutFollowsConfiguredVerticalTraceOrientation() {
        Map<String, Object> layout = LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of("title", Map.of("text", "Turnaround Time")),
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            "Newport Beach",
            List.of(Map.of(
                "type", "bar",
                "orientation", "v"
            ))
        );

        assertEquals(Map.of("automargin", true), layout.get("xaxis"));
        assertEquals(Map.of("title", "Non-Conformance Status"), layout.get("yaxis"));
        assertEquals(Map.of("b", 80, "l", 60, "r", 20, "t", 45), layout.get("margin"));
    }

    @Test
    void derivedBarLayoutKeepsHorizontalTraceOrientation() {
        Map<String, Object> layout = LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of("title", Map.of("text", "Turnaround Time")),
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            "Newport Beach",
            List.of(Map.of(
                "type", "bar",
                "orientation", "h"
            ))
        );

        assertEquals(Map.of("title", "Non-Conformance Status"), layout.get("xaxis"));
        assertEquals(Map.of("automargin", true), layout.get("yaxis"));
        assertEquals(Map.of("b", 40, "l", 150, "r", 20, "t", 45), layout.get("margin"));
    }

    @Test
    void derivedBarLayoutPreservesCustomValueAxisTitle() {
        Map<String, Object> verticalLayout = LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of("yaxis", Map.of("title", "Custom count")),
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            "Newport Beach",
            List.of(Map.of("type", "bar", "orientation", "v"))
        );

        Map<String, Object> horizontalLayout = LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of("xaxis", Map.of("title", Map.of("text", "Custom duration"))),
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            "Newport Beach",
            List.of(Map.of("type", "bar", "orientation", "h"))
        );

        assertEquals("Custom count", ((Map<?, ?>) verticalLayout.get("yaxis")).get("title"));
        assertEquals(
            Map.of("text", "Custom duration"),
            ((Map<?, ?>) horizontalLayout.get("xaxis")).get("title")
        );
    }

    @Test
    void importMetadataDefaultsPreserveCustomYAxisTitle() {
        Map<String, Object> layout = LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
            Map.of("yaxis", Map.of("title", Map.of("text", "Samples tested"))),
            new LocationDashboardImportStrategyConfig.GraphConfig(
                "newport-water-quality",
                "Water Quality Conformance",
                "Newport Beach",
                LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                "newport-beach",
                List.of(),
                Map.of(),
                "scatter"
            ),
            "Newport Beach"
        );

        assertEquals(
            Map.of(
                "title", Map.of("text", "Samples tested"),
                "rangemode", "tozero",
                "dtick", 1
            ),
            layout.get("yaxis")
        );
    }

    @Test
    void importMetadataDefaultsMigrateKnownLegacyYAxisTitle() {
        Map<String, Object> layout = LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
            Map.of("yaxis", Map.of("title", "% Compliance", "ticksuffix", "%")),
            new LocationDashboardImportStrategyConfig.GraphConfig(
                "newport-water-quality",
                "Water Quality Conformance",
                "Newport Beach",
                LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                "newport-beach",
                List.of(),
                Map.of(),
                "scatter"
            ),
            "Newport Beach"
        );

        assertEquals(
            Map.of("title", "# Non-Conformances", "rangemode", "tozero", "dtick", 1),
            layout.get("yaxis")
        );
    }
}
