package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationDashboardGraphMatcherTest {
    private final LocationDashboardGraphMatcher matcher = new LocationDashboardGraphMatcher();

    @Test
    void availableImportMatchingSkipsMissingDefinitionsForDashboardReads() {
        Graph graph = graph("Water Quality Conformance", "Newport Beach");

        Map<String, Graph> matches = matcher.matchAvailableImportGraphs(
            List.of(
                graphDefinition("newport-water-quality", "Water Quality Conformance", "Newport Beach"),
                graphDefinition("irvine-water-quality", "Water Quality Conformance", "Irvine")
            ),
            List.of(graph),
            "Hoag Hospital"
        );

        assertEquals(List.of("newport water quality"), matches.keySet().stream().toList());
        assertEquals(graph, matches.get("newport water quality"));
    }

    @Test
    void strictImportMatchingStillRejectsMissingDefinitionsForImports() {
        ApiClientException exception = assertThrows(ApiClientException.class, () ->
            matcher.matchImportGraphs(
                List.of(graphDefinition("irvine-water-quality", "Water Quality Conformance", "Irvine")),
                List.of(graph("Water Quality Conformance", "Newport Beach")),
                "Hoag Hospital"
            )
        );

        assertEquals("location_dashboard_graph_not_found", exception.getCode());
    }

    private LocationDashboardImportStrategyConfig.GraphConfig graphDefinition(
        String id,
        String name,
        String title
    ) {
        return new LocationDashboardImportStrategyConfig.GraphConfig(
            id,
            name,
            title,
            LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
            null,
            List.of(),
            Map.of(),
            "scatter"
        );
    }

    private Graph graph(String name, String title) {
        Graph graph = new Graph();
        graph.setId(System.nanoTime());
        graph.setName(name);
        graph.setLayout(Map.of("title", Map.of("text", title)));
        return graph;
    }
}
