package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDashboardGraphMatcherTest {
    private final LocationDashboardGraphMatcher matcher = new LocationDashboardGraphMatcher();

    @Test
    void matchImportGraphsIgnoresMissingConfiguredGraphs() {
        Map<String, Graph> matches = matcher.matchImportGraphs(
            List.of(
                graphDefinition("newport", "Water Quality Conformance", "Newport"),
                graphDefinition("irvine", "Water Quality Conformance", "Irvine")
            ),
            List.of(graph(10L, "Water Quality Conformance", "Newport")),
            "Hoag"
        );

        assertEquals(List.of("newport"), matches.keySet().stream().toList());
        assertEquals(10L, matches.get("newport").getId());
    }

    @Test
    void matchImportGraphsUsesFirstDuplicateMatchAndIgnoresExtras() {
        Map<String, Graph> matches = matcher.matchImportGraphs(
            List.of(graphDefinition("newport", "Water Quality Conformance", "Newport")),
            List.of(
                graph(10L, "Water Quality Conformance", "Newport"),
                graph(11L, "Water Quality Conformance", "Newport")
            ),
            "Hoag"
        );

        assertEquals(1, matches.size());
        assertEquals(10L, matches.get("newport").getId());
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
            WATER_QUALITY_COMPLIANCE,
            "newport",
            List.of("HPC"),
            Map.of("HPC", "#1f77b4"),
            "scatter"
        );
    }

    private Graph graph(Long id, String name, String title) {
        Graph graph = new Graph();
        graph.setId(id);
        graph.setName(name);
        graph.setLayout(Map.of("title", Map.of("text", title)));
        graph.setData(List.of(Map.of("type", "scatter", "name", "HPC", "x", List.of(), "y", List.of())));
        return graph;
    }
}
