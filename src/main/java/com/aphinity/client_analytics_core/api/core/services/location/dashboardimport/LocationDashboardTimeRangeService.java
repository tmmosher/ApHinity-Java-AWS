package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRangePayloadProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;

@Service
public class LocationDashboardTimeRangeService {
    private static final Logger log = LoggerFactory.getLogger(LocationDashboardTimeRangeService.class);
    private static final ZoneId PHOENIX_ZONE = ZoneId.of("America/Phoenix");

    private final LocationRepository locationRepository;
    private final LocationGraphRepository locationGraphRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final LocationDashboardImportStrategyRegistry strategyRegistry;
    private final Clock clock;
    private final LocationDashboardGraphMatcher graphMatcher = new LocationDashboardGraphMatcher();
    private final LocationDashboardHistoricalDataAssembler historicalDataAssembler;

    @Autowired
    public LocationDashboardTimeRangeService(
        LocationRepository locationRepository,
        LocationGraphRepository locationGraphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationDashboardImportStrategyRegistry strategyRegistry
    ) {
        this(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            Clock.system(PHOENIX_ZONE)
        );
    }

    LocationDashboardTimeRangeService(
        LocationRepository locationRepository,
        LocationGraphRepository locationGraphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        Clock clock
    ) {
        this.locationRepository = locationRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.strategyRegistry = strategyRegistry;
        this.clock = clock;
        this.historicalDataAssembler = new LocationDashboardHistoricalDataAssembler(
            new LocationDashboardCorrectiveActionService(serviceEventRepository, clock, strategyRegistry)
        );
    }

    @Transactional
    public void refreshLocationDateGroups(Long locationId) {
        RefreshContext refreshContext = loadRefreshContext(locationId);
        if (refreshContext == null) {
            return;
        }

        strategyRegistry.resolve(refreshContext.location().getName()).ifPresent(strategy ->
            refreshDerivedGraphs(
                locationId,
                refreshContext.location(),
                refreshContext.assignedGraphs(),
                strategy,
                refreshContext.refreshedAt()
            )
        );

        log.info(
            "Refreshed location dashboard derived graphs locationId={} graphCount={} anchorDate={}",
            locationId,
            refreshContext.assignedGraphs().size(),
            refreshContext.anchorDate()
        );
    }

    @Transactional
    public void refreshLocationImportedGraphDateGroups(Long locationId) {
        RefreshContext refreshContext = loadRefreshContext(locationId);
        if (refreshContext == null) {
            return;
        }

        log.info(
            "Skipped location dashboard imported graph time-range materialization locationId={} graphCount={} anchorDate={}",
            locationId,
            refreshContext.assignedGraphs().size(),
            refreshContext.anchorDate()
        );
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Map<String, Object>>> resolveLocationMonthRangePayloads(
        Long locationId,
        DashboardGraphMonthRange monthRange
    ) {
        RefreshContext refreshContext = loadRefreshContext(locationId);
        if (refreshContext == null) {
            return Map.of();
        }
        DashboardGraphMonthRange normalizedRange = monthRange == null ? DashboardGraphMonthRange.ALL_TIME : monthRange;
        if (normalizedRange.isAllTime()) {
            return refreshDerivedGraphsForResponse(
                locationId,
                refreshContext.location(),
                refreshContext.assignedGraphs(),
                normalizedRange,
                refreshContext.anchorDate(),
                refreshContext.refreshedAt()
            );
        }

        Map<Long, List<Map<String, Object>>> payloadsByGraphId = new LinkedHashMap<>();
        for (Graph graph : refreshContext.assignedGraphs()) {
            if (graph == null || graph.getId() == null || isDerivedGraph(graph)) {
                continue;
            }
            List<Map<String, Object>> allTimePayload =
                GraphRelationalPayloadMapper.normalize(graph).data();
            payloadsByGraphId.put(
                graph.getId(),
                graphContainsTimeSeries(graph)
                    ? DashboardGraphMonthRangePayloadProjector.project(allTimePayload, normalizedRange, refreshContext.anchorDate())
                    : allTimePayload
            );
        }

        payloadsByGraphId.putAll(refreshDerivedGraphsForResponse(
            locationId,
            refreshContext.location(),
            refreshContext.assignedGraphs(),
            normalizedRange,
            refreshContext.anchorDate(),
            refreshContext.refreshedAt()
        ));
        return Map.copyOf(payloadsByGraphId);
    }

    private RefreshContext loadRefreshContext(Long locationId) {
        if (locationId == null) {
            return null;
        }
        Location location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return null;
        }

        List<LocationGraph> locationGraphs = locationGraphRepository.findByLocationIdWithGraphDetails(locationId);
        if (locationGraphs.isEmpty()) {
            return null;
        }

        List<Graph> assignedGraphs = locationGraphs.stream()
            .map(LocationGraph::getGraph)
            .filter(Objects::nonNull)
            .toList();
        return new RefreshContext(
            location,
            assignedGraphs,
            LocalDate.now(clock),
            Instant.now(clock)
        );
    }

    private void refreshDerivedGraphs(
        Long locationId,
        Location location,
        List<Graph> assignedGraphs,
        LocationDashboardImportStrategy strategy,
        Instant refreshedAt
    ) {
        Map<String, Graph> matchedImportGraphsByDefinitionId = graphMatcher.matchImportGraphs(
            strategy.graphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        Map<String, Graph> matchedDerivedGraphsByDefinitionId = graphMatcher.matchDerivedGraphs(
            strategy.derivedGraphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        if (matchedDerivedGraphsByDefinitionId.isEmpty()) {
            return;
        }

        Map<Long, Graph> assignedGraphsById = assignedGraphs.stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
        List<ServiceEvent> correctiveActions =
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(locationId);
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData allTimeHistoricalData =
            historicalDataAssembler.buildHistoricalDerivedData(
                strategy.graphDefinitions(),
                matchedImportGraphsByDefinitionId,
                assignedGraphsById,
                assignedGraphsById,
                List.of(),
                correctiveActions
            );

        for (DerivedGraphConfig derivedGraphDefinition : strategy.derivedGraphDefinitions()) {
            if (derivedGraphDefinition == null) {
                continue;
            }
            Graph graph = matchedDerivedGraphsByDefinitionId.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(derivedGraphDefinition.id())
            );
            if (graph == null) {
                continue;
            }
            if (shouldPreserveExistingResolutionGraph(derivedGraphDefinition, graph)) {
                continue;
            }
            graph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
                graph.getLayout(),
                derivedGraphDefinition,
                strategy.locationName()
            ));
            graph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            GraphRelationalPayloadMapper.syncGraphData(
                graph,
                LocationDashboardDerivedGraphSupport.buildPayload(derivedGraphDefinition, graph, allTimeHistoricalData)
            );
        }
    }

    private Map<Long, List<Map<String, Object>>> refreshDerivedGraphsForResponse(
        Long locationId,
        Location location,
        List<Graph> assignedGraphs,
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate,
        Instant refreshedAt
    ) {
        return strategyRegistry.resolve(location.getName())
            .map(strategy -> buildDerivedGraphPayloadsForResponse(
                locationId,
                location,
                assignedGraphs,
                strategy,
                monthRange,
                anchorDate,
                refreshedAt
            ))
            .orElseGet(Map::of);
    }

    private Map<Long, List<Map<String, Object>>> buildDerivedGraphPayloadsForResponse(
        Long locationId,
        Location location,
        List<Graph> assignedGraphs,
        LocationDashboardImportStrategy strategy,
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate,
        Instant refreshedAt
    ) {
        Map<String, Graph> matchedImportGraphsByDefinitionId = graphMatcher.matchImportGraphs(
            strategy.graphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        Map<String, Graph> matchedDerivedGraphsByDefinitionId = graphMatcher.matchDerivedGraphs(
            strategy.derivedGraphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        if (matchedDerivedGraphsByDefinitionId.isEmpty()) {
            return Map.of();
        }

        Map<Long, Graph> assignedGraphsById = assignedGraphs.stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
        List<ServiceEvent> correctiveActions =
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(locationId);
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            historicalDataAssembler.buildHistoricalDerivedData(
                strategy.graphDefinitions(),
                matchedImportGraphsByDefinitionId,
                assignedGraphsById,
                assignedGraphsById,
                List.of(),
                correctiveActions
            );
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData rangedHistoricalData =
            HistoricalDerivedDataTimeRangeProjector.project(historicalData, monthRange, anchorDate);

        Map<Long, List<Map<String, Object>>> payloadsByGraphId = new LinkedHashMap<>();
        for (DerivedGraphConfig derivedGraphDefinition : strategy.derivedGraphDefinitions()) {
            if (derivedGraphDefinition == null) {
                continue;
            }
            Graph graph = matchedDerivedGraphsByDefinitionId.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(derivedGraphDefinition.id())
            );
            if (graph == null || graph.getId() == null) {
                continue;
            }
            if (shouldPreserveExistingResolutionGraph(derivedGraphDefinition, graph)) {
                if (monthRange != null && !monthRange.isAllTime()) {
                    payloadsByGraphId.put(graph.getId(), storedPayloadForMonthRange(graph));
                }
                continue;
            }
            payloadsByGraphId.put(
                graph.getId(),
                LocationDashboardDerivedGraphSupport.buildPayload(derivedGraphDefinition, graph, rangedHistoricalData)
            );
        }
        return payloadsByGraphId;
    }

    private List<Map<String, Object>> storedPayloadForMonthRange(Graph graph) {
        if (graph == null) {
            return List.of();
        }
        return GraphRelationalPayloadMapper.normalize(graph).data();
    }

    private boolean shouldPreserveExistingResolutionGraph(DerivedGraphConfig derivedGraphDefinition, Graph graph) {
        LocationDashboardImportStrategyConfig.DerivedGraphType graphDerivedType = graphDerivedType(graph);
        if (graphDerivedType != null) {
            return graphDerivedType.requiresResolvedNonConformanceState();
        }
        if (derivedGraphDefinition == null || derivedGraphDefinition.derivedType() == null) {
            return false;
        }
        // Generic dashboard refreshes do not have spreadsheet analyzed-sample context.
        // These resolution-driven derived graphs must retain the payload produced by
        // the spreadsheet analyzer until the next import recomputes them.
        return derivedGraphDefinition.derivedType().requiresResolvedNonConformanceState();
    }

    private LocationDashboardImportStrategyConfig.DerivedGraphType graphDerivedType(Graph graph) {
        if (graph == null) {
            return null;
        }
        Map<String, String> importMetadata = LocationDashboardGraphMetadataSupport.readImportMetadata(graph);
        String rawDerivedGraphType = importMetadata.get("derivedGraphType");
        if (rawDerivedGraphType == null || rawDerivedGraphType.isBlank()) {
            return null;
        }
        try {
            return LocationDashboardImportStrategyConfig.DerivedGraphType.fromValue(rawDerivedGraphType);
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Ignoring unknown dashboard derived graph type graphId={} derivedGraphType={}",
                graph.getId(),
                rawDerivedGraphType
            );
            return null;
        }
    }

    private boolean graphContainsTimeSeries(Graph graph) {
        if (graph == null || graph.getGraphTraces() == null) {
            return false;
        }
        for (GraphTrace graphTrace : graph.getGraphTraces()) {
            if (graphTrace == null) {
                continue;
            }
            if ("time_series".equalsIgnoreCase(graphTrace.getDataMode())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDerivedGraph(Graph graph) {
        Map<String, String> importMetadata = LocationDashboardGraphMetadataSupport.readImportMetadata(graph);
        return importMetadata.containsKey("derivedGraphType");
    }

    private record RefreshContext(
        Location location,
        List<Graph> assignedGraphs,
        LocalDate anchorDate,
        Instant refreshedAt
    ) {
    }
}
