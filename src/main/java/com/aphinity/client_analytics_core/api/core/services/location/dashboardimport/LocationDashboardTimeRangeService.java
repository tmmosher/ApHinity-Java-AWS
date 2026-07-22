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
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRangePayloadProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;

/**
 * Builds and refreshes dashboard graph payloads for finite month ranges.
 * <p>
 * Imported graphs can often be projected from their all-time payloads, while
 * derived graphs must be recomputed from persisted samples and corrective-action
 * events. This service centralizes that distinction so dashboard reads and
 * scheduled refreshes use the same range semantics.
 */
@Service
public class LocationDashboardTimeRangeService {
    private static final Logger log = LoggerFactory.getLogger(LocationDashboardTimeRangeService.class);

    private final LocationRepository locationRepository;
    private final LocationGraphRepository locationGraphRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final DashboardImportStrategyResolver strategyRegistry;
    private final LocationDashboardSamplePersistenceService samplePersistenceService;
    private final Clock clock;
    private final LocationDashboardCache dashboardCache;
    private final LocationDashboardGraphMatcher graphMatcher;
    private final LocationDashboardHistoricalDataAssembler historicalDataAssembler;

    public record MonthRangeGraphProjection(
        List<Map<String, Object>> data,
        Map<String, Object> layout
    ) {
    }

    @Autowired
    public LocationDashboardTimeRangeService(
        LocationRepository locationRepository,
        LocationGraphRepository locationGraphRepository,
        ServiceEventRepository serviceEventRepository,
        DashboardImportStrategyResolver strategyRegistry,
        LocationDashboardSamplePersistenceService samplePersistenceService,
        LocationDashboardCache dashboardCache,
        LocationDashboardGraphMatcher graphMatcher,
        LocationDashboardHistoricalDataAssembler historicalDataAssembler,
        Clock clock
    ) {
        this.locationRepository = locationRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.strategyRegistry = strategyRegistry;
        this.samplePersistenceService = samplePersistenceService;
        this.clock = clock;
        this.dashboardCache = dashboardCache;
        this.graphMatcher = graphMatcher;
        this.historicalDataAssembler = historicalDataAssembler;
    }

    /**
     * Invalidates all derived and range-projected dashboard values for a location.
     * Mutating services call this after successful persistence.
     */
    public void invalidateLocationCache(Long locationId) {
        dashboardCache.invalidateLocation(locationId);
    }

    /**
     * Refreshes persisted all-time derived graphs for one location.
     * This is used by the scheduler to keep derived graph rows aligned with
     * persisted sample history and corrective actions even when no user is
     * requesting the dashboard.
     *
     * @param locationId location whose derived graph payloads should be refreshed
     */
    @Transactional
    public void refreshLocationDateGroups(Long locationId) {
        dashboardCache.invalidateLocation(locationId);
        try {
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
        } finally {
            // The refresh itself can populate the historical cache. Do not leave
            // a snapshot created before the surrounding write transaction commits.
            dashboardCache.invalidateLocation(locationId);
        }
    }

    /**
     * Placeholder hook for imported graph range materialization.
     * Imported graphs are currently projected at response time, so this method
     * intentionally logs that no persistent imported-graph range refresh is needed.
     *
     * @param locationId location considered for imported graph range materialization
     */
    @Transactional
    public void refreshLocationImportedGraphDateGroups(Long locationId) {
        dashboardCache.invalidateLocation(locationId);
        try {
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
        } finally {
            dashboardCache.invalidateLocation(locationId);
        }
    }

    /**
     * Resolves graph data overrides for a finite dashboard month range.
     * Non-derived imported graphs are projected from their all-time relational
     * payload. Derived graphs are rebuilt from persisted historical data for the
     * requested range so rollups and corrective-action overlays remain consistent.
     *
     * @param locationId location whose graph payloads are being requested
     * @param monthRange requested range; {@code null} and all-time requests return no overrides
     * @return graph id to replacement Plotly data payload, for graphs needing a range override
     */
    @Transactional
    public Map<Long, List<Map<String, Object>>> resolveLocationMonthRangePayloads(
        Long locationId,
        DashboardGraphMonthRange monthRange
    ) {
        return resolveLocationMonthRangeProjections(locationId, monthRange).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().data(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    @Transactional
    public Map<Long, MonthRangeGraphProjection> resolveLocationMonthRangeProjections(
        Long locationId,
        DashboardGraphMonthRange monthRange
    ) {
        RefreshContext refreshContext = loadRefreshContext(locationId);
        if (refreshContext == null) {
            return Map.of();
        }
        DashboardGraphMonthRange normalizedRange = monthRange == null ? DashboardGraphMonthRange.ALL_TIME : monthRange;
        if (normalizedRange.isAllTime()) {
            refreshDerivedGraphsForResponse(
                locationId,
                refreshContext.location(),
                refreshContext.assignedGraphs(),
                normalizedRange,
                refreshContext.anchorDate(),
                refreshContext.refreshedAt(),
                null
            );
            return Map.of();
        }

        Map<Long, MonthRangeGraphProjection> projectionsByGraphId = new LinkedHashMap<>();
        Map<Long, Graph> graphsById = refreshContext.assignedGraphs().stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
        Set<Long> missingDerivedGraphIds = new LinkedHashSet<>();
        for (Graph graph : refreshContext.assignedGraphs()) {
            if (graph == null || graph.getId() == null) {
                continue;
            }
            LocationDashboardCache.GraphProjectionCacheKey cacheKey =
                graphProjectionCacheKey(refreshContext, graph, normalizedRange);
            MonthRangeGraphProjection cachedProjection = dashboardCache.getGraphProjection(cacheKey);
            if (cachedProjection != null) {
                projectionsByGraphId.put(graph.getId(), cachedProjection);
                continue;
            }
            if (isDerivedGraph(graph)) {
                missingDerivedGraphIds.add(graph.getId());
                continue;
            }

            MonthRangeGraphProjection projection = projectImportedGraph(graph, normalizedRange, refreshContext.anchorDate());
            dashboardCache.putGraphProjection(cacheKey, projection);
            projectionsByGraphId.put(graph.getId(), projection);
        }

        if (!missingDerivedGraphIds.isEmpty()) {
            refreshDerivedGraphsForResponse(
                locationId,
                refreshContext.location(),
                refreshContext.assignedGraphs(),
                normalizedRange,
                refreshContext.anchorDate(),
                refreshContext.refreshedAt(),
                missingDerivedGraphIds
            ).forEach((graphId, payload) -> {
                Graph graph = graphsById.get(graphId);
                MonthRangeGraphProjection projection = new MonthRangeGraphProjection(
                    payload,
                    DashboardGraphMonthRangePayloadProjector.projectLayout(
                        graph == null ? null : graph.getLayout(),
                        normalizedRange,
                        refreshContext.anchorDate(),
                        payload
                    )
                );
                dashboardCache.putGraphProjection(
                    graphProjectionCacheKey(refreshContext, graph, normalizedRange),
                    projection
                );
                projectionsByGraphId.put(graphId, projection);
            });
        }
        return Map.copyOf(projectionsByGraphId);
    }

    @Transactional(readOnly = true)
    public LocationDashboardTablePageResponse resolveRecentSampleMeasurementsPage(
        Long locationId,
        Long graphId,
        Integer monthRange,
        Integer page,
        Integer size
    ) {
        RefreshContext refreshContext = loadRefreshContext(locationId);
        if (refreshContext == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location dashboard table not found");
        }
        int normalizedSize = Math.max(1, Math.min(size == null ? 10 : size, 100));
        int requestedPage = Math.max(1, page == null ? 1 : page);
        DashboardGraphMonthRange normalizedRange = DashboardGraphMonthRange.fromRequestValue(monthRange);

        return strategyRegistry.resolve(refreshContext.location().getName())
            .map(strategy -> buildRecentSampleMeasurementsPage(
                locationId,
                graphId,
                refreshContext,
                strategy,
                normalizedRange,
                requestedPage,
                normalizedSize
            ))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location dashboard table not found"));
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
        Map<String, Graph> matchedImportGraphsByDefinitionId = graphMatcher.matchAvailableImportGraphs(
            strategy.graphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        Map<String, Graph> matchedDerivedGraphsByDefinitionId = graphMatcher.matchAvailableDerivedGraphs(
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
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData allTimeHistoricalData = loadHistoricalData(
            locationId,
            location,
            strategy,
            matchedImportGraphsByDefinitionId,
            assignedGraphsById
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
                strategy.locationName(),
                LocationDashboardGraphMetadataSupport.currentTraceList(graph)
            ));
            graph.setStyle(LocationDashboardGraphMetadataSupport.withDerivedImportStyle(
                graph.getStyle(),
                derivedGraphDefinition
            ));
            graph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            GraphRelationalPayloadMapper.syncGraphData(
                graph,
                LocationDashboardDerivedGraphSupport.buildPayload(
                    derivedGraphDefinition,
                    graph,
                    allTimeHistoricalData,
                    strategy.spreadsheetIdentityPattern(),
                    LocalDate.now(clock)
                )
            );
            graph.setUpdatedAt(refreshedAt);
        }
    }

    private Map<Long, List<Map<String, Object>>> refreshDerivedGraphsForResponse(
        Long locationId,
        Location location,
        List<Graph> assignedGraphs,
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate,
        Instant refreshedAt,
        Set<Long> requestedGraphIds
    ) {
        return strategyRegistry.resolve(location.getName())
            .map(strategy -> buildDerivedGraphPayloadsForResponse(
                locationId,
                location,
                assignedGraphs,
                strategy,
                monthRange,
                anchorDate,
                refreshedAt,
                requestedGraphIds
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
        Instant refreshedAt,
        Set<Long> requestedGraphIds
    ) {
        Map<String, Graph> matchedImportGraphsByDefinitionId = graphMatcher.matchAvailableImportGraphs(
            strategy.graphDefinitions(),
            assignedGraphs,
            location.getName()
        );
        Map<String, Graph> matchedDerivedGraphsByDefinitionId = graphMatcher.matchAvailableDerivedGraphs(
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
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData = loadHistoricalData(
            locationId,
            location,
            strategy,
            matchedImportGraphsByDefinitionId,
            assignedGraphsById
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
            if (monthRange != null && !monthRange.isAllTime()
                && requestedGraphIds != null
                && !requestedGraphIds.contains(graph.getId())) {
                continue;
            }
            if (monthRange == null || monthRange.isAllTime()) {
                GraphRelationalPayloadMapper.syncGraphData(
                    graph,
                    LocationDashboardDerivedGraphSupport.buildPayload(
                        derivedGraphDefinition,
                        graph,
                        rangedHistoricalData,
                        strategy.spreadsheetIdentityPattern(),
                        anchorDate
                    )
                );
                graph.setUpdatedAt(refreshedAt);
                continue;
            }
            payloadsByGraphId.put(
                graph.getId(),
                LocationDashboardDerivedGraphSupport.buildPayload(
                    derivedGraphDefinition,
                    graph,
                    rangedHistoricalData,
                    strategy.spreadsheetIdentityPattern(),
                    anchorDate
                )
            );
        }
        return payloadsByGraphId;
    }

    private LocationDashboardTablePageResponse buildRecentSampleMeasurementsPage(
        Long locationId,
        Long graphId,
        RefreshContext refreshContext,
        LocationDashboardImportStrategy strategy,
        DashboardGraphMonthRange monthRange,
        int requestedPage,
        int pageSize
    ) {
        Map<String, Graph> matchedImportGraphsByDefinitionId = graphMatcher.matchAvailableImportGraphs(
            strategy.graphDefinitions(),
            refreshContext.assignedGraphs(),
            refreshContext.location().getName()
        );
        Map<String, Graph> matchedDerivedGraphsByDefinitionId = graphMatcher.matchAvailableDerivedGraphs(
            strategy.derivedGraphDefinitions(),
            refreshContext.assignedGraphs(),
            refreshContext.location().getName()
        );

        DerivedGraphConfig tableGraphDefinition = strategy.derivedGraphDefinitions().stream()
            .filter(Objects::nonNull)
            .filter(definition -> definition.derivedType()
                == LocationDashboardImportStrategyConfig.DerivedGraphType.RECENT_SAMPLE_MEASUREMENTS)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location dashboard table not found"));
        Graph tableGraph = matchedDerivedGraphsByDefinitionId.get(
            LocationDashboardGraphMetadataSupport.normalizeKey(tableGraphDefinition.id())
        );
        if (tableGraph == null || tableGraph.getId() == null || !Objects.equals(tableGraph.getId(), graphId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location dashboard table not found");
        }

        Map<Long, Graph> assignedGraphsById = refreshContext.assignedGraphs().stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData = loadHistoricalData(
            locationId,
            refreshContext.location(),
            strategy,
            matchedImportGraphsByDefinitionId,
            assignedGraphsById
        );
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData rangedHistoricalData =
            HistoricalDerivedDataTimeRangeProjector.project(historicalData, monthRange, refreshContext.anchorDate());
        LocationDashboardDerivedGraphSupport.RecentSampleMeasurementsTable table =
            LocationDashboardDerivedGraphSupport.buildRecentSampleMeasurementsTable(
                rangedHistoricalData.recentRawSamples(),
                strategy.spreadsheetIdentityPattern(),
                refreshContext.anchorDate()
            );

        long total = table.rows().size();
        int lastPage = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int responsePage = Math.min(requestedPage, lastPage);
        int fromIndex = Math.min((responsePage - 1) * pageSize, table.rows().size());
        int toIndex = Math.min(fromIndex + pageSize, table.rows().size());
        return new LocationDashboardTablePageResponse(
            table.rows().subList(fromIndex, toIndex),
            lastPage,
            total,
            responsePage,
            pageSize
        );
    }

    private MonthRangeGraphProjection projectImportedGraph(
        Graph graph,
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate
    ) {
        List<Map<String, Object>> allTimePayload = GraphRelationalPayloadMapper.normalize(graph).data();
        List<Map<String, Object>> projectedPayload = graphContainsTimeSeries(graph)
            ? DashboardGraphMonthRangePayloadProjector.project(allTimePayload, monthRange, anchorDate)
            : allTimePayload;
        return new MonthRangeGraphProjection(
            projectedPayload,
            DashboardGraphMonthRangePayloadProjector.projectLayout(
                graph.getLayout(),
                monthRange,
                anchorDate,
                projectedPayload
            )
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalDerivedData loadHistoricalData(
        Long locationId,
        Location location,
        LocationDashboardImportStrategy strategy,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<Long, Graph> assignedGraphsById
    ) {
        LocationDashboardCache.HistoricalDataCacheKey cacheKey =
            new LocationDashboardCache.HistoricalDataCacheKey(locationId, locationRevision(location));
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData cachedHistoricalData =
            dashboardCache.getHistoricalData(cacheKey);
        if (cachedHistoricalData != null) {
            return cachedHistoricalData;
        }

        List<ServiceEvent> correctiveActions =
            serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(locationId);
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> persistedSamples =
            samplePersistenceService.loadLocationSamples(locationId);
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            historicalDataAssembler.buildHistoricalDerivedData(
                strategy.graphDefinitions(),
                matchedImportGraphsByDefinitionId,
                assignedGraphsById,
                assignedGraphsById,
                persistedSamples,
                correctiveActions,
                strategy.spreadsheetIdentityPattern()
            );
        dashboardCache.putHistoricalData(cacheKey, historicalData);
        return historicalData;
    }

    private LocationDashboardCache.GraphProjectionCacheKey graphProjectionCacheKey(
        RefreshContext refreshContext,
        Graph graph,
        DashboardGraphMonthRange monthRange
    ) {
        if (refreshContext == null || graph == null || graph.getId() == null || monthRange == null) {
            return null;
        }
        return new LocationDashboardCache.GraphProjectionCacheKey(
            refreshContext.location().getId(),
            graph.getId(),
            monthRange.months(),
            refreshContext.anchorDate(),
            locationRevision(refreshContext.location()),
            graphRevision(graph)
        );
    }

    private Instant locationRevision(Location location) {
        return location == null || location.getUpdatedAt() == null
            ? Instant.EPOCH
            : location.getUpdatedAt();
    }

    private Instant graphRevision(Graph graph) {
        return graph == null || graph.getUpdatedAt() == null
            ? Instant.EPOCH
            : graph.getUpdatedAt();
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
