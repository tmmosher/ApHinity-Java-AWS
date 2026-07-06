package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.MeasurementBoundRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardSpreadsheetUploadResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.services.location.GraphResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

/**
 * Imports configured dashboard spreadsheets into the graphs assigned to a location.
 * <p>
 * The service chooses a location-specific import strategy, validates that the
 * workbook identity matches the location, computes imported and derived graph
 * payloads, and optionally persists raw sample history used by time-range views.
 * Imports run under a location-scoped database lock so two concurrent uploads
 * cannot interleave graph and corrective-action changes for the same location.
 */
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
    private final LocationDashboardSamplePersistenceService samplePersistenceService;
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
        LocationDashboardSamplePersistenceService samplePersistenceService,
        GraphResponseMapper graphResponseMapper
    ) {
        this(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            serviceEventRepository,
            mutationLockService,
            samplePersistenceService,
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
        LocationDashboardSamplePersistenceService samplePersistenceService,
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
            samplePersistenceService,
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
        LocationDashboardSamplePersistenceService samplePersistenceService,
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
        this.samplePersistenceService = samplePersistenceService;
        this.historicalDataAssembler = new LocationDashboardHistoricalDataAssembler(correctiveActionService);
        this.graphResponseMapper = graphResponseMapper;
        this.clock = clock;
    }

    /**
     * Imports a dashboard workbook without replacing the persisted sample history.
     * This mode updates graph payload previews and corrective-action previews but
     * leaves the raw sample table untouched.
     *
     * @param location location whose assigned dashboard graphs should be updated
     * @param file uploaded dashboard workbook
     * @return updated graph payloads and corrective-action drafts
     */
    @Transactional
    public LocationDashboardSpreadsheetUploadResponse importLocationDashboard(Location location, org.springframework.web.multipart.MultipartFile file) {
        return importLocationDashboard(location, file, false);
    }

    /**
     * Imports a dashboard workbook and optionally replaces persisted sample history.
     * Persisted samples are used by derived historical/time-range graphs; callers
     * should enable {@code persistSamples} only for committed uploads, not transient
     * preview flows.
     *
     * @param location location whose assigned dashboard graphs should be updated
     * @param file uploaded dashboard workbook
     * @param persistSamples whether raw parsed sample observations should replace existing samples
     * @return updated graph payloads and corrective-action drafts
     */
    @Transactional
    public LocationDashboardSpreadsheetUploadResponse importLocationDashboard(
        Location location,
        org.springframework.web.multipart.MultipartFile file,
        boolean persistSamples
    ) {
        if (location == null || location.getId() == null) {
            throw new IllegalArgumentException("Location is required");
        }

        LocationDashboardImportStrategy strategy = strategyRegistry.resolve(location.getName())
            .orElseThrow(() -> new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_strategy_not_found",
                "Dashboard import strategy is not configured for this location."
            ));
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            spreadsheetParser.parse(file, strategy.spreadsheetIdentityPattern());
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
            importDashboardLocked(location, strategy, workbook, measurementBounds, persistSamples)
        );
    }

    private LocationDashboardSpreadsheetUploadResponse importDashboardLocked(
        Location location,
        LocationDashboardImportStrategy strategy,
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        List<com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound> measurementBounds,
        boolean persistSamples
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
        if (persistSamples) {
            samplePersistenceService.replaceLocationSamples(location, computation, previewCorrectiveActions);
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

        return new LocationDashboardSpreadsheetUploadResponse(
            buildResponsesInGraphOrder(
                assignedGraphs,
                previewGraphsById,
                updatedPreviewGraphsById.keySet()
            ),
            previewCorrectiveActions.stream()
                .filter(serviceEvent -> serviceEvent.getId() == null)
                .map(this::toLocationEventRequest)
                .toList()
        );
    }

    private LocationEventRequest toLocationEventRequest(
        com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent serviceEvent
    ) {
        return new LocationEventRequest(
            serviceEvent.getTitle(),
            serviceEvent.getResponsibility(),
            serviceEvent.getEventDate(),
            serviceEvent.getEventTime(),
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime(),
            serviceEvent.getDescription(),
            serviceEvent.getStatus()
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

            boolean resetLegacyPercentHistory = LocationDashboardGraphMetadataSupport.hasLegacyPercentAxis(previewGraph);
            previewGraph.setLayout(LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
                previewGraph.getLayout(),
                graphDefinition,
                strategy.locationName()
            ));
            previewGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(graphDefinition.graphType()));
            previewGraph.setData(importedGraphMerger.mergeImportedGraphData(
                previewGraph,
                computedGraphPayload.data(),
                resetLegacyPercentHistory
            ));
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
                previewCorrectiveActions,
                strategy.spreadsheetIdentityPattern()
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
            previewGraph.setStyle(LocationDashboardGraphMetadataSupport.withDerivedImportStyle(
                previewGraph.getStyle(),
                derivedGraphDefinition
            ));
            previewGraph.setGraphType(LocationDashboardGraphMetadataSupport.normalizeGraphType(derivedGraphDefinition.graphType()));
            previewGraph.setData(LocationDashboardDerivedGraphSupport.buildPayload(
                derivedGraphDefinition,
                previewGraph,
                historicalDerivedData,
                strategy.spreadsheetIdentityPattern()
            ));
            updatedPreviewGraphsById.put(previewGraph.getId(), previewGraph);
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
        Set<Long> updatedGraphIdSet = new LinkedHashSet<>(updatedGraphIds);
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
