package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Configuration adapter for graph capabilities; unconfigured graphs remain range-enabled. */
@Component
public final class ConfiguredDashboardGraphCapabilityQuery implements DashboardGraphCapabilityQuery {
    private final DashboardImportStrategyResolver strategyResolver;
    private final LocationDashboardGraphMatcher graphMatcher;

    public ConfiguredDashboardGraphCapabilityQuery(
        DashboardImportStrategyResolver strategyResolver,
        LocationDashboardGraphMatcher graphMatcher
    ) {
        this.strategyResolver = strategyResolver;
        this.graphMatcher = graphMatcher;
    }

    @Override
    public Map<Long, Boolean> resolveSectionTimeRangeCapabilities(String locationName, Collection<Graph> graphs) {
        List<Graph> availableGraphs = graphs == null
            ? List.of()
            : graphs.stream().filter(Objects::nonNull).filter(graph -> graph.getId() != null).toList();
        Map<Long, Boolean> capabilities = new LinkedHashMap<>();
        availableGraphs.forEach(graph -> capabilities.put(graph.getId(), true));
        strategyResolver.resolve(locationName).ifPresent(strategy -> {
            Map<String, Graph> importedGraphs = graphMatcher.matchAvailableImportGraphs(
                strategy.graphDefinitions(), availableGraphs, locationName
            );
            strategy.graphDefinitions().forEach(definition -> {
                Graph graph = importedGraphs.get(LocationDashboardGraphMetadataSupport.normalizeKey(definition.id()));
                if (graph != null && graph.getId() != null) {
                    capabilities.put(graph.getId(), definition.supportsSectionTimeRange());
                }
            });

            Map<String, Graph> derivedGraphs = graphMatcher.matchAvailableDerivedGraphs(
                strategy.derivedGraphDefinitions(), availableGraphs, locationName
            );
            strategy.derivedGraphDefinitions().forEach(definition -> {
                Graph graph = derivedGraphs.get(LocationDashboardGraphMetadataSupport.normalizeKey(definition.id()));
                if (graph != null && graph.getId() != null) {
                    capabilities.put(graph.getId(), definition.supportsSectionTimeRange());
                }
            });
        });
        return Map.copyOf(capabilities);
    }
}
