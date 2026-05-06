package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDashboardImportedGraphMergerTest {
    private final LocationDashboardImportedGraphMerger merger = new LocationDashboardImportedGraphMerger();

    @Test
    void mergeImportedGraphDataPreservesExistingTimeSeriesWhenImportedTraceIsEmpty() {
        Graph graph = graph(List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of("2025-07-01"),
            "y", List.of(100.0d),
            "customdata", List.of(Map.of("sampleCount", 3, "compliantCount", 3, "nonConformingCount", 0)),
            "mode", "lines+markers"
        )));

        List<Map<String, Object>> merged = merger.mergeImportedGraphData(graph, List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of(),
            "y", List.of(),
            "customdata", List.of(),
            "mode", "lines+markers"
        )));

        assertEquals(List.of("2025-07-01"), merged.getFirst().get("x"));
        assertNumericValues(merged.getFirst().get("y"), 100.0d);
    }

    @Test
    void mergeImportedGraphDataUpsertsPointsByObservedDate() {
        Graph graph = graph(List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of("2025-07-01", "2025-08-01"),
            "y", List.of(100.0d, 100.0d),
            "customdata", List.of(
                Map.of("sampleCount", 3, "compliantCount", 3, "nonConformingCount", 0),
                Map.of("sampleCount", 4, "compliantCount", 4, "nonConformingCount", 0)
            ),
            "mode", "lines+markers"
        )));

        List<Map<String, Object>> merged = merger.mergeImportedGraphData(graph, List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of("2025-08-01", "2025-09-01"),
            "y", List.of(0.0d, 100.0d),
            "customdata", List.of(
                Map.of("sampleCount", 2, "compliantCount", 0, "nonConformingCount", 2),
                Map.of("sampleCount", 5, "compliantCount", 5, "nonConformingCount", 0)
            ),
            "mode", "lines+markers"
        )));

        assertEquals(List.of("2025-07-01", "2025-08-01", "2025-09-01"), merged.getFirst().get("x"));
        assertNumericValues(merged.getFirst().get("y"), 100.0d, 0.0d, 100.0d);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) merged.getFirst().get("customdata");
        assertEquals(2L, ((Number) customData.get(1).get("sampleCount")).longValue());
    }

    private void assertNumericValues(Object rawValues, double... expectedValues) {
        @SuppressWarnings("unchecked")
        List<Number> values = (List<Number>) rawValues;
        assertEquals(expectedValues.length, values.size());
        for (int index = 0; index < expectedValues.length; index += 1) {
            assertEquals(expectedValues[index], values.get(index).doubleValue());
        }
    }

    private Graph graph(List<Map<String, Object>> data) {
        Graph graph = new Graph();
        graph.setId(1L);
        graph.setName("Water Quality Compliance");
        graph.setGraphType("scatter");
        graph.setLayout(new LinkedHashMap<>());
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        graph.setData(data);
        return graph;
    }
}
