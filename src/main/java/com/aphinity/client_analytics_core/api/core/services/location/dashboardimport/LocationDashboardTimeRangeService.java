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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
        if (locationId == null) {
            return;
        }
        Location location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return;
        }

        List<LocationGraph> locationGraphs = locationGraphRepository.findByLocationIdWithGraphDetails(locationId);
        if (locationGraphs.isEmpty()) {
            return;
        }

        List<Graph> assignedGraphs = locationGraphs.stream()
            .map(LocationGraph::getGraph)
            .filter(Objects::nonNull)
            .toList();
        LocalDate anchorDate = LocalDate.now(clock);
        Instant refreshedAt = Instant.now(clock);

        for (Graph graph : assignedGraphs) {
            if (graph == null || graph.getId() == null || isDerivedGraph(graph)) {
                continue;
            }
            materializeRollingTimeRanges(graph, anchorDate);
            graph.setUpdatedAt(refreshedAt);
        }

        strategyRegistry.resolve(location.getName()).ifPresent(strategy ->
            refreshDerivedGraphs(locationId, location, assignedGraphs, strategy, anchorDate, refreshedAt)
        );

        log.info(
            "Refreshed location dashboard time ranges locationId={} graphCount={} anchorDate={}",
            locationId,
            assignedGraphs.size(),
            anchorDate
        );
    }

    private void materializeRollingTimeRanges(Graph graph, LocalDate anchorDate) {
        List<Map<String, Object>> allTimePayload = GraphRelationalPayloadMapper.normalize(graph, GraphTimeRange.ALL_TIME).data();
        if (allTimePayload.isEmpty()) {
            GraphRelationalPayloadMapper.syncGraphData(graph, List.of(), GraphTimeRange.ONE_MONTH);
            GraphRelationalPayloadMapper.syncGraphData(graph, List.of(), GraphTimeRange.THREE_MONTHS);
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
                filterTimeSeriesPayloadForRange(allTimePayload, timeRange, anchorDate),
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
            graph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
                graph.getLayout(),
                derivedGraphDefinition,
                strategy.locationName()
            ));
            graph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            for (GraphTimeRange timeRange : GraphTimeRange.values()) {
                LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
                    filterHistoricalDataForRange(allTimeHistoricalData, timeRange, anchorDate);
                GraphRelationalPayloadMapper.syncGraphData(
                    graph,
                    LocationDashboardDerivedGraphSupport.buildPayload(derivedGraphDefinition, graph, historicalData),
                    timeRange
                );
            }
            graph.setUpdatedAt(refreshedAt);
        }
    }

    private List<Map<String, Object>> filterTimeSeriesPayloadForRange(
        List<Map<String, Object>> allTimePayload,
        GraphTimeRange timeRange,
        LocalDate anchorDate
    ) {
        if (timeRange == GraphTimeRange.ALL_TIME) {
            return allTimePayload;
        }
        LocalDate windowStart = timeRange.windowStartInclusive(anchorDate);
        if (windowStart == null) {
            return allTimePayload;
        }

        List<Map<String, Object>> filteredPayload = new ArrayList<>(allTimePayload.size());
        for (Map<String, Object> trace : allTimePayload) {
            filteredPayload.add(filterTraceForRange(trace, windowStart));
        }
        return List.copyOf(filteredPayload);
    }

    private Map<String, Object> filterTraceForRange(Map<String, Object> trace, LocalDate windowStart) {
        if (!isTimeSeriesTrace(trace)) {
            return trace;
        }

        List<?> xValues = LocationDashboardGraphMetadataSupport.asList(trace.get("x"));
        List<?> yValues = LocationDashboardGraphMetadataSupport.asList(trace.get("y"));
        List<?> customDataValues = LocationDashboardGraphMetadataSupport.asList(trace.get("customdata"));
        int pointCount = Math.min(xValues.size(), yValues.size());

        List<Object> filteredXValues = new ArrayList<>();
        List<Object> filteredYValues = new ArrayList<>();
        List<Object> filteredCustomDataValues = new ArrayList<>();
        boolean hasCustomData = false;

        for (int index = 0; index < pointCount; index += 1) {
            LocalDate observedDate = LocationDashboardGraphMetadataSupport.parseLocalDate(xValues.get(index));
            if (observedDate == null || observedDate.isBefore(windowStart)) {
                continue;
            }
            filteredXValues.add(xValues.get(index));
            filteredYValues.add(yValues.get(index));
            Object customDataValue = index < customDataValues.size() ? customDataValues.get(index) : null;
            filteredCustomDataValues.add(customDataValue);
            if (customDataValue != null) {
                hasCustomData = true;
            }
        }

        Map<String, Object> filteredTrace = new LinkedHashMap<>(trace);
        filteredTrace.put("x", List.copyOf(filteredXValues));
        filteredTrace.put("y", List.copyOf(filteredYValues));
        if (hasCustomData) {
            filteredTrace.put("customdata", List.copyOf(filteredCustomDataValues));
        } else {
            filteredTrace.remove("customdata");
        }
        return filteredTrace;
    }

    private LocationDashboardDerivedGraphSupport.HistoricalDerivedData filterHistoricalDataForRange(
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData,
        GraphTimeRange timeRange,
        LocalDate anchorDate
    ) {
        if (historicalData == null || timeRange == GraphTimeRange.ALL_TIME) {
            return historicalData;
        }
        LocalDate windowStart = timeRange.windowStartInclusive(anchorDate);
        if (windowStart == null) {
            return historicalData;
        }

        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> filteredSamplesByDate =
            historicalData.samplesByDate().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBefore(windowStart))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()),
                    (left, right) -> left,
                    LinkedHashMap::new
                ));

        List<LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction> filteredCorrectiveActions =
            historicalData.correctiveActions().stream()
                .filter(correctiveAction -> correctiveAction != null
                    && correctiveAction.observedDate() != null
                    && !correctiveAction.observedDate().isBefore(windowStart))
                .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
            filteredSamplesByDate,
            filteredCorrectiveActions
        );
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

    private boolean isTimeSeriesTrace(Map<String, Object> trace) {
        if (!Objects.equals(
            LocationDashboardGraphMetadataSupport.normalizeKey(String.valueOf(trace.get("type"))),
            "scatter"
        )) {
            return false;
        }
        List<?> xValues = LocationDashboardGraphMetadataSupport.asList(trace.get("x"));
        return !xValues.isEmpty()
            && xValues.stream().allMatch(value -> LocationDashboardGraphMetadataSupport.parseLocalDate(value) != null);
    }

    private Set<GraphTimeRange> rollingRanges() {
        Set<GraphTimeRange> rollingRanges = new LinkedHashSet<>();
        rollingRanges.add(GraphTimeRange.ONE_MONTH);
        rollingRanges.add(GraphTimeRange.THREE_MONTHS);
        return rollingRanges;
    }
}
