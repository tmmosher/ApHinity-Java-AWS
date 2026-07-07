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
}
