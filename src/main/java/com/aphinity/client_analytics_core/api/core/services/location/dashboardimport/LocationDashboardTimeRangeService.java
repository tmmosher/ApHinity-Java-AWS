package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.services.location.GraphTimeRangePayloadProjector;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

        materializeImportedGraphTimeRanges(
            refreshContext.assignedGraphs(),
            refreshContext.anchorDate(),
            refreshContext.refreshedAt()
        );

        strategyRegistry.resolve(refreshContext.location().getName()).ifPresent(strategy ->
            refreshDerivedGraphs(
                locationId,
                refreshContext.location(),
                refreshContext.assignedGraphs(),
                strategy,
                refreshContext.anchorDate(),
                refreshContext.refreshedAt()
            )
        );

        log.info(
            "Refreshed location dashboard time ranges locationId={} graphCount={} anchorDate={}",
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

        materializeImportedGraphTimeRanges(
            refreshContext.assignedGraphs(),
            refreshContext.anchorDate(),
            refreshContext.refreshedAt()
        );

        log.info(
            "Refreshed location dashboard imported graph time ranges locationId={} graphCount={} anchorDate={}",
            locationId,
            refreshContext.assignedGraphs().size(),
            refreshContext.anchorDate()
        );
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

    private void materializeImportedGraphTimeRanges(
        List<Graph> assignedGraphs,
        LocalDate anchorDate,
        Instant refreshedAt
    ) {
        if (assignedGraphs == null || assignedGraphs.isEmpty()) {
            return;
        }

        for (Graph graph : assignedGraphs) {
            if (graph == null || graph.getId() == null || isDerivedGraph(graph)) {
                continue;
            }
            materializeRollingTimeRanges(graph, anchorDate);
            graph.setUpdatedAt(refreshedAt);
        }
    }

    private void materializeRollingTimeRanges(Graph graph, LocalDate anchorDate) {
        List<Map<String, Object>> allTimePayload = GraphRelationalPayloadMapper.normalize(graph, GraphTimeRange.ALL_TIME).data();
        if (allTimePayload.isEmpty()) {
            GraphRelationalPayloadMapper.syncGraphData(graph, List.of(), GraphTimeRange.THREE_MONTHS);
            GraphRelationalPayloadMapper.syncGraphData(graph, List.of(), GraphTimeRange.TWELVE_MONTHS);
            return;
        }

        if (!graphContainsTimeSeries(graph)) {
            for (GraphTimeRange timeRange : rollingRanges()) {
                GraphRelationalPayloadMapper.syncGraphData(graph, allTimePayload, timeRange);
            }
            return;
        }

        for (GraphTimeRange timeRange : rollingRanges()) {
            GraphRelationalPayloadMapper.syncGraphData(
                graph,
                GraphTimeRangePayloadProjector.project(allTimePayload, timeRange, anchorDate),
                timeRange
            );
        }
    }

    private void refreshDerivedGraphs(
        Long locationId,
        Location location,
        List<Graph> assignedGraphs,
        LocationDashboardImportStrategy strategy,
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
            if (shouldPreserveExistingResolutionGraph(derivedGraphDefinition, correctiveActions)) {
                continue;
            }
            graph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
                graph.getLayout(),
                derivedGraphDefinition,
                strategy.locationName()
            ));
            graph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            for (GraphTimeRange timeRange : GraphTimeRange.values()) {
                LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
                    HistoricalDerivedDataTimeRangeProjector.project(allTimeHistoricalData, timeRange, anchorDate);
                GraphRelationalPayloadMapper.syncGraphData(
                    graph,
                    LocationDashboardDerivedGraphSupport.buildPayload(derivedGraphDefinition, graph, historicalData),
                    timeRange
                );
            }
            graph.setUpdatedAt(refreshedAt);
        }
    }

    private boolean shouldPreserveExistingResolutionGraph(
        DerivedGraphConfig derivedGraphDefinition,
        List<ServiceEvent> correctiveActions
    ) {
        if (derivedGraphDefinition == null || derivedGraphDefinition.derivedType() == null) {
            return false;
        }
        if (!derivedGraphDefinition.derivedType().requiresResolvedNonConformanceState()) {
            return false;
        }
        // Generic dashboard refreshes do not have spreadsheet analyzed-sample context.
        // When there are also no persisted corrective actions, recomputing these
        // resolution-driven derived graphs would destructively collapse them to empty.
        return correctiveActions == null || correctiveActions.isEmpty();
    }

    private boolean graphContainsTimeSeries(Graph graph) {
        if (graph == null || graph.getGraphTraces() == null) {
            return false;
        }
        for (GraphTrace graphTrace : graph.getGraphTraces()) {
            if (graphTrace == null || graphTrace.getTimeRange() != GraphTimeRange.ALL_TIME) {
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

    private Set<GraphTimeRange> rollingRanges() {
        Set<GraphTimeRange> rollingRanges = new LinkedHashSet<>();
        rollingRanges.add(GraphTimeRange.THREE_MONTHS);
        rollingRanges.add(GraphTimeRange.TWELVE_MONTHS);
        return rollingRanges;
    }

    private record RefreshContext(
        Location location,
        List<Graph> assignedGraphs,
        LocalDate anchorDate,
        Instant refreshedAt
    ) {
    }
}
