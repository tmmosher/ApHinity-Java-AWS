package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.MeasurementBoundRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private final GraphRepository graphRepository;
    private final LocationRepository locationRepository;
    private final LocationDashboardMutationLockService mutationLockService;
    private final LocationDashboardGraphMatcher graphMatcher;
    private final LocationDashboardImportedGraphMerger importedGraphMerger;
    private final LocationDashboardCorrectiveActionService correctiveActionService;
    private final LocationDashboardHistoricalDataAssembler historicalDataAssembler;

    @Autowired
    public LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        GraphRepository graphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationRepository locationRepository,
        LocationDashboardMutationLockService mutationLockService
    ) {
        this(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            graphRepository,
            serviceEventRepository,
            locationRepository,
            mutationLockService,
            Clock.systemDefaultZone()
        );
    }

    LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        GraphRepository graphRepository,
        ServiceEventRepository serviceEventRepository,
        LocationRepository locationRepository,
        LocationDashboardMutationLockService mutationLockService,
        Clock clock
    ) {
        this(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            graphRepository,
            locationRepository,
            mutationLockService,
            new LocationDashboardGraphMatcher(),
            new LocationDashboardImportedGraphMerger(),
            new LocationDashboardCorrectiveActionService(serviceEventRepository, clock)
        );
    }

    LocationDashboardImportService(
        LocationDashboardSpreadsheetParser spreadsheetParser,
        LocationDashboardImportStrategyRegistry strategyRegistry,
        MeasurementBoundRepository measurementBoundRepository,
        LocationGraphRepository locationGraphRepository,
        GraphRepository graphRepository,
        LocationRepository locationRepository,
        LocationDashboardMutationLockService mutationLockService,
        LocationDashboardGraphMatcher graphMatcher,
        LocationDashboardImportedGraphMerger importedGraphMerger,
        LocationDashboardCorrectiveActionService correctiveActionService
    ) {
        this.spreadsheetParser = spreadsheetParser;
        this.strategyRegistry = strategyRegistry;
        this.measurementBoundRepository = measurementBoundRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.graphRepository = graphRepository;
        this.locationRepository = locationRepository;
        this.mutationLockService = mutationLockService;
        this.graphMatcher = graphMatcher;
        this.importedGraphMerger = importedGraphMerger;
        this.correctiveActionService = correctiveActionService;
        this.historicalDataAssembler = new LocationDashboardHistoricalDataAssembler(correctiveActionService);
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

        Map<Long, Graph> lockedGraphsById = lockAssignedGraphs(location, assignedGraphs);
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation =
            strategy.computeImport(workbook, measurementBounds);
        correctiveActionService.upsertCorrectiveActions(location, computation.correctiveActions());

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

        Map<Long, Graph> graphsToPersistById = applyImportedGraphUpdates(
            strategy,
            computation.graphs(),
            matchedImportGraphsByDefinitionId,
            lockedGraphsById,
            graphDefinitionsById
        );
        applyDerivedGraphUpdates(
            location.getId(),
            strategy,
            matchedImportGraphsByDefinitionId,
            matchedDerivedGraphsByDefinitionId,
            lockedGraphsById,
            graphsToPersistById,
            derivedGraphDefinitionsById
        );

        try {
            graphRepository.saveAllAndFlush(new ArrayList<>(graphsToPersistById.values()));
            List<GraphResponse> responses = buildResponsesInGraphOrder(
                assignedGraphs,
                lockedGraphsById,
                graphsToPersistById.keySet()
            );
            locationRepository.touchUpdatedAt(location.getId(), Instant.now());
            return responses;
        } catch (RuntimeException ex) {
            log.error(
                "Dashboard spreadsheet import persistence failed locationId={} locationName={}",
                location.getId(),
                location.getName(),
                ex
            );
            throw ex;
        }
    }

    private Map<Long, Graph> lockAssignedGraphs(Location location, List<Graph> assignedGraphs) {
        List<Long> targetGraphIds = assignedGraphs.stream()
            .map(Graph::getId)
            .filter(Objects::nonNull)
            .toList();
        List<Graph> lockedGraphs = graphRepository.findByLocationIdAndGraphIdInForUpdate(location.getId(), targetGraphIds);
        if (lockedGraphs.size() != targetGraphIds.size()) {
            throw new ApiClientException(
                HttpStatus.CONFLICT,
                "graph_update_conflict",
                "Graph update conflict"
            );
        }
        return lockedGraphs.stream()
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, Graph> applyImportedGraphUpdates(
        LocationDashboardImportStrategy strategy,
        List<LocationDashboardImportStrategy.ComputedGraphPayload> computedGraphs,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<Long, Graph> lockedGraphsById,
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
            Graph lockedGraph = lockedGraphsById.get(matchedGraph.getId());
            if (lockedGraph == null) {
                continue;
            }
            GraphConfig graphDefinition = graphDefinitionsById.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(computedGraphPayload.graphId())
            );
            if (graphDefinition == null) {
                continue;
            }

            lockedGraph.setLayout(LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
                lockedGraph.getLayout(),
                graphDefinition,
                strategy.locationName()
            ));
            lockedGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(graphDefinition.graphType()));
            lockedGraph.setData(importedGraphMerger.mergeImportedGraphData(lockedGraph, computedGraphPayload.data()));
            graphsToPersistById.put(lockedGraph.getId(), lockedGraph);
        }
        return graphsToPersistById;
    }

    private void applyDerivedGraphUpdates(
        Long locationId,
        LocationDashboardImportStrategy strategy,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<String, Graph> matchedDerivedGraphsByDefinitionId,
        Map<Long, Graph> lockedGraphsById,
        Map<Long, Graph> graphsToPersistById,
        Map<String, DerivedGraphConfig> derivedGraphDefinitionsById
    ) {
        if (derivedGraphDefinitionsById.isEmpty()) {
            return;
        }

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalDerivedData =
            historicalDataAssembler.buildHistoricalDerivedData(
                strategy.graphDefinitions(),
                matchedImportGraphsByDefinitionId,
                lockedGraphsById,
                graphsToPersistById,
                correctiveActionService.findPersistedCorrectiveActions(locationId)
            );

        for (Map.Entry<String, DerivedGraphConfig> entry : derivedGraphDefinitionsById.entrySet()) {
            Graph matchedGraph = matchedDerivedGraphsByDefinitionId.get(entry.getKey());
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph lockedGraph = lockedGraphsById.get(matchedGraph.getId());
            if (lockedGraph == null) {
                continue;
            }

            DerivedGraphConfig derivedGraphDefinition = entry.getValue();
            lockedGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
                lockedGraph.getLayout(),
                derivedGraphDefinition,
                strategy.locationName()
            ));
            lockedGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            lockedGraph.setData(LocationDashboardDerivedGraphSupport.buildPayload(
                derivedGraphDefinition,
                lockedGraph,
                historicalDerivedData
            ));
            graphsToPersistById.put(lockedGraph.getId(), lockedGraph);
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
        Map<Long, Graph> lockedGraphsById,
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
            Graph lockedGraph = lockedGraphsById.get(assignedGraph.getId());
            if (lockedGraph == null) {
                continue;
            }
            responses.add(toGraphResponse(lockedGraph));
        }
        return List.copyOf(responses);
    }

    private GraphResponse toGraphResponse(Graph graph) {
        GraphPayloadMapper.GraphPayload payload;
        try {
            payload = GraphRelationalPayloadMapper.normalize(graph);
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Invalid graph payload for graphId={} during dashboard import response mapping",
                graph.getId(),
                ex
            );
            throw new ApiClientException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "graph_data_invalid",
                "Graph data is invalid"
            );
        }

        return new GraphResponse(
            graph.getId(),
            graph.getName(),
            payload.data(),
            payload.layout(),
            payload.config(),
            payload.style(),
            graph.getCreatedAt(),
            graph.getUpdatedAt()
        );
    }
}
