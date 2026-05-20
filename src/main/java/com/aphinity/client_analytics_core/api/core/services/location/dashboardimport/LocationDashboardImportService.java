package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.MeasurementBoundRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.services.location.GraphResponseMapper;
import com.aphinity.client_analytics_core.api.core.services.location.GraphTimeRangePayloadProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;

@Service
public class LocationDashboardImportService {
    private static final Logger log = LoggerFactory.getLogger(LocationDashboardImportService.class);

    private final LocationDashboardSpreadsheetParser spreadsheetParser;
    private final LocationDashboardImportStrategyRegistry strategyRegistry;
    private final MeasurementBoundRepository measurementBoundRepository;
    private final LocationGraphRepository locationGraphRepository;
    private final LocationDashboardMutationLockService mutationLockService;
    private final LocationDashboardGraphMatcher graphMatcher;
    private final LocationDashboardImportedGraphMerger importedGraphMerger;
    private final LocationDashboardCorrectiveActionService correctiveActionService;
    private final LocationDashboardHistoricalDataAssembler historicalDataAssembler;
    private final GraphResponseMapper graphResponseMapper;
    private final Clock clock;

    @Autowired
    public LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationDashboardMutationLockService mutationLockService,
        GraphResponseMapper graphResponseMapper
    ) {
        this(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            serviceEventRepository,
            mutationLockService,
            graphResponseMapper,
            Clock.systemDefaultZone()
        );
    }

    LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationDashboardMutationLockService mutationLockService,
        GraphResponseMapper graphResponseMapper,
        Clock clock
    ) {
        this(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            mutationLockService,
            new LocationDashboardGraphMatcher(),
            new LocationDashboardImportedGraphMerger(),
            new LocationDashboardCorrectiveActionService(serviceEventRepository, clock, strategyRegistry),
            graphResponseMapper,
            clock
        );
    }

    LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        LocationDashboardMutationLockService mutationLockService,
        LocationDashboardGraphMatcher graphMatcher,
        LocationDashboardImportedGraphMerger importedGraphMerger,
        LocationDashboardCorrectiveActionService correctiveActionService,
        GraphResponseMapper graphResponseMapper,
        Clock clock
    ) {
        this.spreadsheetParser = spreadsheetParser;
        this.strategyRegistry = strategyRegistry;
        this.measurementBoundRepository = measurementBoundRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.mutationLockService = mutationLockService;
        this.graphMatcher = graphMatcher;
        this.importedGraphMerger = importedGraphMerger;
        this.correctiveActionService = correctiveActionService;
        this.historicalDataAssembler = new LocationDashboardHistoricalDataAssembler(correctiveActionService);
        this.graphResponseMapper = graphResponseMapper;
        this.clock = clock;
    }

    @Transactional
    public List<GraphResponse> importLocationDashboard(Location location, org.springframework.web.multipart.MultipartFile file) {
        if (location == null || location.getId() == null) {
            throw new IllegalArgumentException("Location is required");
        }

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = spreadsheetParser.parse(file);
        LocationDashboardImportStrategy strategy = strategyRegistry.resolve(location.getName())
            .orElseThrow(() -> new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_strategy_not_found",
                "Dashboard import strategy is not configured for this location."
            ));
        requireMatchingLocationTitle(workbook.locationTitle(), location.getName(), strategy.locationName());

        List<com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound> measurementBounds =
            measurementBoundRepository.findByLocationId(location.getId());
        if (measurementBounds.isEmpty()) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_measurements_not_configured",
                "Dashboard measurements are not configured for this location."
            );
        }

        return mutationLockService.executeWithLocationLock(location.getId(), () ->
            importDashboardLocked(location, strategy, workbook, measurementBounds)
        );
    }

    private List<GraphResponse> importDashboardLocked(
        Location location,
        LocationDashboardImportStrategy strategy,
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        List<com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound> measurementBounds
    ) {
        List<Graph> assignedGraphs = locationGraphRepository.findByLocationIdWithGraph(location.getId()).stream()
            .map(LocationGraph::getGraph)
            .toList();

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

        Map<Long, Graph> assignedGraphsById = assignedGraphs.stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
        Map<Long, Graph> previewGraphsById = cloneAssignedGraphsById(assignedGraphs);
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation =
            strategy.computeImport(workbook, measurementBounds);
        List<com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent> previewCorrectiveActions =
            correctiveActionService.buildPreviewCorrectiveActions(location.getId(), computation.correctiveActions());
        for (com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent previewCorrectiveAction : previewCorrectiveActions) {
            if (previewCorrectiveAction != null && previewCorrectiveAction.getLocation() == null) {
                previewCorrectiveAction.setLocation(location);
            }
        }

        Map<String, GraphConfig> graphDefinitionsById = strategy.graphDefinitions().stream()
            .collect(Collectors.toMap(
                graphDefinition -> LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.id()),
                graphDefinition -> graphDefinition,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, DerivedGraphConfig> derivedGraphDefinitionsById = strategy.derivedGraphDefinitions().stream()
            .collect(Collectors.toMap(
                graphDefinition -> LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.id()),
                graphDefinition -> graphDefinition,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        Map<Long, Graph> updatedPreviewGraphsById = applyImportedGraphUpdates(
            strategy,
            computation.graphs(),
            matchedImportGraphsByDefinitionId,
            previewGraphsById,
            graphDefinitionsById
        );
        applyDerivedGraphUpdates(
            strategy,
            computation,
            matchedImportGraphsByDefinitionId,
            matchedDerivedGraphsByDefinitionId,
            assignedGraphsById,
            previewGraphsById,
            updatedPreviewGraphsById,
            previewCorrectiveActions,
            derivedGraphDefinitionsById
        );

        return buildResponsesInGraphOrder(
            assignedGraphs,
            previewGraphsById,
            updatedPreviewGraphsById.keySet()
        );
    }

    private Map<Long, Graph> applyImportedGraphUpdates(
        LocationDashboardImportStrategy strategy,
        List<LocationDashboardImportStrategy.ComputedGraphPayload> computedGraphs,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<Long, Graph> previewGraphsById,
        Map<String, GraphConfig> graphDefinitionsById
    ) {
        Map<Long, Graph> graphsToPersistById = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.ComputedGraphPayload computedGraphPayload : computedGraphs) {
            Graph matchedGraph = matchedImportGraphsByDefinitionId.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(computedGraphPayload.graphId())
            );
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph previewGraph = previewGraphsById.get(matchedGraph.getId());
            if (previewGraph == null) {
                continue;
            }
            GraphConfig graphDefinition = graphDefinitionsById.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(computedGraphPayload.graphId())
            );
            if (graphDefinition == null) {
                continue;
            }

            previewGraph.setLayout(LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
                previewGraph.getLayout(),
                graphDefinition,
                strategy.locationName()
            ));
            previewGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(graphDefinition.graphType()));
            previewGraph.setData(importedGraphMerger.mergeImportedGraphData(previewGraph, computedGraphPayload.data()));
            materializePreviewRollingRanges(previewGraph);
            graphsToPersistById.put(previewGraph.getId(), previewGraph);
        }
        return graphsToPersistById;
    }

    private void applyDerivedGraphUpdates(
        LocationDashboardImportStrategy strategy,
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<String, Graph> matchedDerivedGraphsByDefinitionId,
        Map<Long, Graph> assignedGraphsById,
        Map<Long, Graph> previewGraphsById,
        Map<Long, Graph> updatedPreviewGraphsById,
        List<com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent> previewCorrectiveActions,
        Map<String, DerivedGraphConfig> derivedGraphDefinitionsById
    ) {
        if (derivedGraphDefinitionsById.isEmpty()) {
            return;
        }

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalDerivedData =
            historicalDataAssembler.buildHistoricalDerivedData(
                strategy.graphDefinitions(),
                matchedImportGraphsByDefinitionId,
                assignedGraphsById,
                updatedPreviewGraphsById,
                computation.analyzedSamples(),
                previewCorrectiveActions
            );

        for (Map.Entry<String, DerivedGraphConfig> entry : derivedGraphDefinitionsById.entrySet()) {
            Graph matchedGraph = matchedDerivedGraphsByDefinitionId.get(entry.getKey());
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph previewGraph = previewGraphsById.get(matchedGraph.getId());
            if (previewGraph == null) {
                continue;
            }

            DerivedGraphConfig derivedGraphDefinition = entry.getValue();
            previewGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
                previewGraph.getLayout(),
                derivedGraphDefinition,
                strategy.locationName()
            ));
            previewGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            previewGraph.setData(LocationDashboardDerivedGraphSupport.buildPayload(
                derivedGraphDefinition,
                previewGraph,
                historicalDerivedData
            ));
            for (GraphTimeRange timeRange : List.of(GraphTimeRange.THREE_MONTHS, GraphTimeRange.TWELVE_MONTHS)) {
                GraphRelationalPayloadMapper.syncGraphData(
                    previewGraph,
                    LocationDashboardDerivedGraphSupport.buildPayload(
                        derivedGraphDefinition,
                        previewGraph,
                        HistoricalDerivedDataTimeRangeProjector.project(historicalDerivedData, timeRange, LocalDate.now(clock))
                    ),
                    timeRange
                );
            }
            updatedPreviewGraphsById.put(previewGraph.getId(), previewGraph);
        }
    }

    private void materializePreviewRollingRanges(Graph graph) {
        List<Map<String, Object>> allTimePayload = GraphRelationalPayloadMapper.normalize(graph, GraphTimeRange.ALL_TIME).data();
        for (GraphTimeRange timeRange : List.of(GraphTimeRange.THREE_MONTHS, GraphTimeRange.TWELVE_MONTHS)) {
            GraphRelationalPayloadMapper.syncGraphData(
                graph,
                GraphTimeRangePayloadProjector.project(allTimePayload, timeRange, LocalDate.now(clock)),
                timeRange
            );
        }
    }

    private void requireMatchingLocationTitle(String workbookTitle, String locationName, String strategyLocationName) {
        String normalizedWorkbookTitle = LocationDashboardGraphMetadataSupport.normalizeKey(workbookTitle);
        if (normalizedWorkbookTitle == null
            || !Objects.equals(normalizedWorkbookTitle, LocationDashboardGraphMetadataSupport.normalizeKey(locationName))
            || !Objects.equals(normalizedWorkbookTitle, LocationDashboardGraphMetadataSupport.normalizeKey(strategyLocationName))) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_location_title_mismatch",
                "Spreadsheet location title does not match the selected location."
            );
        }
    }

    private List<GraphResponse> buildResponsesInGraphOrder(
        List<Graph> assignedGraphs,
        Map<Long, Graph> previewGraphsById,
        Collection<Long> updatedGraphIds
    ) {
        List<GraphResponse> responses = new ArrayList<>();
        Set<Long> updatedGraphIdSet = updatedGraphIds instanceof Set<?>
            ? updatedGraphIds.stream().collect(Collectors.toCollection(LinkedHashSet::new))
            : new LinkedHashSet<>(updatedGraphIds);
        for (Graph assignedGraph : assignedGraphs) {
            if (assignedGraph == null || assignedGraph.getId() == null || !updatedGraphIdSet.contains(assignedGraph.getId())) {
                continue;
            }
            Graph previewGraph = previewGraphsById.get(assignedGraph.getId());
            if (previewGraph == null) {
                continue;
            }
            responses.add(toGraphResponse(previewGraph));
        }
        return List.copyOf(responses);
    }

    private Map<Long, Graph> cloneAssignedGraphsById(List<Graph> assignedGraphs) {
        return assignedGraphs.stream()
            .filter(graph -> graph != null && graph.getId() != null)
            .map(this::copyGraph)
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
    }

    private Graph copyGraph(Graph source) {
        Graph copy = new Graph();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setGraphType(source.getGraphType());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setLayout(LocationDashboardGraphMetadataSupport.copyMutableMap(source.getLayout()));
        copy.setConfig(LocationDashboardGraphMetadataSupport.copyMutableMap(source.getConfig()));
        copy.setStyle(LocationDashboardGraphMetadataSupport.copyMutableMap(source.getStyle()));
        copy.setData(LocationDashboardGraphMetadataSupport.currentTraceList(source));
        return copy;
    }

    private GraphResponse toGraphResponse(Graph graph) {
        return graphResponseMapper.toResponse(graph);
    }
}
