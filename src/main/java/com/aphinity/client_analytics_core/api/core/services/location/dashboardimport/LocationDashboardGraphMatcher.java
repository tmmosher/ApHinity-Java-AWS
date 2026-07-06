package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.springframework.http.HttpStatus;

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
    /**
     * Matches configured imported graphs to the assigned graphs for a location.
     *
     * @param graphDefinitions strategy graph definitions
     * @param assignedGraphs graphs currently assigned to the location
     * @param locationName location name used in error messages
     * @return matched graphs keyed by normalized strategy graph id
     */
    Map<String, Graph> matchImportGraphs(List<GraphConfig> graphDefinitions, List<Graph> assignedGraphs, String locationName) {
        return matchGraphs(
            graphDefinitions.stream()
                .map(graphDefinition -> new GraphIdentity(graphDefinition.id(), graphDefinition.name(), graphDefinition.title()))
                .toList(),
            assignedGraphs,
            locationName,
            this::matchesImportGraphMetadata,
            true
        );
    }

    Map<String, Graph> matchAvailableImportGraphs(
        List<GraphConfig> graphDefinitions,
        List<Graph> assignedGraphs,
        String locationName
    ) {
        return matchGraphs(
            graphDefinitions.stream()
                .map(graphDefinition -> new GraphIdentity(graphDefinition.id(), graphDefinition.name(), graphDefinition.title()))
                .toList(),
            assignedGraphs,
            locationName,
            this::matchesImportGraphMetadata,
            false
        );
    }

    /**
     * Matches configured derived graphs to the assigned graphs for a location.
     *
     * @param graphDefinitions derived graph definitions
     * @param assignedGraphs graphs currently assigned to the location
     * @param locationName location name used in error messages
     * @return matched graphs keyed by normalized strategy graph id
     */
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
            this::matchesDerivedGraphMetadata,
            true
        );
    }

    Map<String, Graph> matchAvailableDerivedGraphs(
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
            this::matchesDerivedGraphMetadata,
            false
        );
    }

    private Map<String, Graph> matchGraphs(
        List<GraphIdentity> graphDefinitions,
        List<Graph> assignedGraphs,
        String locationName,
        BiPredicate<Graph, GraphIdentity> metadataMatcher,
        boolean requireAll
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
            if (metadataMatches.size() > 1) {
                throw ambiguousGraph("metadata", graphDefinition);
            }
            if (metadataMatches.size() == 1) {
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
            if (nameAndTitleMatches.size() > 1) {
                throw ambiguousGraph("name/title", graphDefinition);
            }
            if (nameAndTitleMatches.size() == 1) {
                Graph matchedGraph = nameAndTitleMatches.getFirst();
                reservedGraphIds.add(matchedGraph.getId());
                matchedGraphsByDefinitionId.put(normalizedDefinitionId, matchedGraph);
                continue;
            }

            if (normalizedDefinitionTitle != null) {
                if (!requireAll) {
                    continue;
                }
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_not_found",
                        "Required dashboard graph was not found for "
                        + locationName + ": " + graphDefinition.name() + " / " + graphDefinition.title() + "."
                );
            }

            List<Graph> nameMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> Objects.equals(
                    LocationDashboardGraphMetadataSupport.normalizeKey(graph.getName()),
                    normalizedDefinitionName
                ))
                .toList();
            if (nameMatches.size() > 1) {
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_invalid",
                    "Dashboard graph name is ambiguous for " + graphDefinition.name() + "."
                );
            }
            if (nameMatches.isEmpty()) {
                if (!requireAll) {
                    continue;
                }
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_not_found",
                        "Required dashboard graph was not found for "
                        + locationName + ": " + graphDefinition.name() + " / " + graphDefinition.title() + "."
                );
            }
            Graph matchedGraph = nameMatches.getFirst();
            reservedGraphIds.add(matchedGraph.getId());
            matchedGraphsByDefinitionId.put(normalizedDefinitionId, matchedGraph);
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

    private ApiClientException ambiguousGraph(String discriminator, GraphIdentity graphDefinition) {
        return new ApiClientException(
            HttpStatus.BAD_REQUEST,
            "location_dashboard_graph_invalid",
            "Dashboard graph " + discriminator + " is ambiguous for " + graphDefinition.name() + " / " + graphDefinition.title() + "."
        );
    }

    private record GraphIdentity(
        String id,
        String name,
        String title
    ) {
    }
}
