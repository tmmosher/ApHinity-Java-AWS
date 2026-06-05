package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;

/**
 * Resolves persisted location graphs against strategy definitions.
 * Import metadata is preferred. When a strategy definition declares a title, matching must be
 * exact on name + title so same-name graphs remain unambiguous.
 */
final class LocationDashboardGraphMatcher {
    Map<String, Graph> matchImportGraphs(List<GraphConfig> graphDefinitions, List<Graph> assignedGraphs, String locationName) {
        return matchGraphs(
            graphDefinitions.stream()
                .map(graphDefinition -> new GraphIdentity(graphDefinition.id(), graphDefinition.name(), graphDefinition.title()))
                .toList(),
            assignedGraphs,
            locationName,
            this::matchesImportGraphMetadata
        );
    }

    Map<String, Graph> matchDerivedGraphs(
        List<DerivedGraphConfig> graphDefinitions,
        List<Graph> assignedGraphs,
        String locationName
    ) {
        return matchGraphs(
            graphDefinitions.stream()
                .map(graphDefinition -> new GraphIdentity(graphDefinition.id(), graphDefinition.name(), graphDefinition.title()))
                .toList(),
            assignedGraphs,
            locationName,
            this::matchesDerivedGraphMetadata
        );
    }

    private Map<String, Graph> matchGraphs(
        List<GraphIdentity> graphDefinitions,
        List<Graph> assignedGraphs,
        String locationName,
        BiPredicate<Graph, GraphIdentity> metadataMatcher
    ) {
        Map<String, Graph> matchedGraphsByDefinitionId = new LinkedHashMap<>();
        Set<Long> reservedGraphIds = new LinkedHashSet<>();

        for (GraphIdentity graphDefinition : graphDefinitions) {
            String normalizedDefinitionId = LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.id());
            String normalizedDefinitionName = LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.name());
            String normalizedDefinitionTitle = LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.title());

            List<Graph> metadataMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> metadataMatcher.test(graph, graphDefinition))
                .toList();
            if (!metadataMatches.isEmpty()) {
                Graph matchedGraph = metadataMatches.getFirst();
                reservedGraphIds.add(matchedGraph.getId());
                matchedGraphsByDefinitionId.put(normalizedDefinitionId, matchedGraph);
                continue;
            }

            List<Graph> nameAndTitleMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> Objects.equals(
                    LocationDashboardGraphMetadataSupport.normalizeKey(graph.getName()),
                    normalizedDefinitionName
                ))
                .filter(graph -> Objects.equals(
                    LocationDashboardGraphMetadataSupport.normalizeKey(
                        LocationDashboardGraphMetadataSupport.readGraphLayoutTitleText(graph)
                    ),
                    normalizedDefinitionTitle
                ))
                .toList();
            if (!nameAndTitleMatches.isEmpty()) {
                Graph matchedGraph = nameAndTitleMatches.getFirst();
                reservedGraphIds.add(matchedGraph.getId());
                matchedGraphsByDefinitionId.put(normalizedDefinitionId, matchedGraph);
                continue;
            }

            if (normalizedDefinitionTitle != null) {
                continue;
            }

            List<Graph> nameMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> Objects.equals(
                    LocationDashboardGraphMetadataSupport.normalizeKey(graph.getName()),
                    normalizedDefinitionName
                ))
                .toList();
            if (!nameMatches.isEmpty()) {
                Graph matchedGraph = nameMatches.getFirst();
                reservedGraphIds.add(matchedGraph.getId());
                matchedGraphsByDefinitionId.put(normalizedDefinitionId, matchedGraph);
            }
        }

        return matchedGraphsByDefinitionId;
    }

    private boolean matchesImportGraphMetadata(Graph graph, GraphIdentity graphIdentity) {
        Map<String, String> metadata = LocationDashboardGraphMetadataSupport.readImportMetadata(graph);
        if (Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("graphId")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.id())
        )) {
            return true;
        }
        return Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("graphName")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.name())
        ) && Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("graphTitle")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.title())
        );
    }

    private boolean matchesDerivedGraphMetadata(Graph graph, GraphIdentity graphIdentity) {
        Map<String, String> metadata = LocationDashboardGraphMetadataSupport.readImportMetadata(graph);
        if (Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("derivedGraphId")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.id())
        )) {
            return true;
        }
        return Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("graphName")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.name())
        ) && Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(metadata.get("graphTitle")),
            LocationDashboardGraphMetadataSupport.normalizeKey(graphIdentity.title())
        );
    }

    private record GraphIdentity(
        String id,
        String name,
        String title
    ) {
    }
}
