package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;

@Service
public class LocationDashboardImportService {
    private static final Logger log = LoggerFactory.getLogger(LocationDashboardImportService.class);
    private static final LocalTime ALL_DAY_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime ALL_DAY_END_TIME = LocalTime.of(23, 59, 59);
    private static final String IMPORT_LAYOUT_META_KEY = "aphinityImport";

    private final LocationDashboardSpreadsheetParser spreadsheetParser;
    private final LocationDashboardImportStrategyRegistry strategyRegistry;
    private final MeasurementBoundRepository measurementBoundRepository;
    private final LocationGraphRepository locationGraphRepository;
    private final GraphRepository graphRepository;
    private final ServiceEventRepository serviceEventRepository;
    private final LocationRepository locationRepository;
    private final LocationDashboardMutationLockService mutationLockService;
    private final Clock clock;

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
        this.spreadsheetParser = spreadsheetParser;
        this.strategyRegistry = strategyRegistry;
        this.measurementBoundRepository = measurementBoundRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.graphRepository = graphRepository;
        this.serviceEventRepository = serviceEventRepository;
        this.locationRepository = locationRepository;
        this.mutationLockService = mutationLockService;
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

        Map<String, Graph> matchedGraphsByDefinitionId = matchGraphs(strategy.graphDefinitions(), assignedGraphs, location.getName());
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
        Map<Long, Graph> lockedGraphsById = lockedGraphs.stream()
            .collect(Collectors.toMap(Graph::getId, graph -> graph, (left, right) -> left, LinkedHashMap::new));

        LocationDashboardImportStrategy.LocationDashboardImportComputation computation =
            strategy.computeImport(workbook, measurementBounds);
        List<LocationDashboardDerivedGraphSupport.CorrectiveActionState> correctiveActionStates =
            upsertCorrectiveActions(location, computation.correctiveActions());
        Map<String, GraphConfig> graphDefinitionsById = strategy.graphDefinitions().stream()
            .collect(Collectors.toMap(
                graphDefinition -> normalizeKey(graphDefinition.id()),
                graphDefinition -> graphDefinition,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        Map<Long, Graph> graphsToPersistById = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.ComputedGraphPayload computedGraphPayload : computation.graphs()) {
            Graph matchedGraph = matchedGraphsByDefinitionId.get(normalizeKey(computedGraphPayload.graphId()));
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph lockedGraph = lockedGraphsById.get(matchedGraph.getId());
            if (lockedGraph == null) {
                continue;
            }
            GraphConfig graphDefinition = graphDefinitionsById.get(normalizeKey(computedGraphPayload.graphId()));
            if (graphDefinition == null) {
                continue;
            }
            lockedGraph.setLayout(withImportMetadataAndDefaults(lockedGraph.getLayout(), graphDefinition, strategy.locationName()));
            lockedGraph.setGraphType(normalizeGraphType(graphDefinition.graphType()));
            lockedGraph.setData(computedGraphPayload.data());
            graphsToPersistById.put(lockedGraph.getId(), lockedGraph);
        }

        List<LocationDashboardDerivedGraphSupport.DerivedGraphUpdate> derivedGraphUpdates =
            LocationDashboardDerivedGraphSupport.buildUpdates(
                lockedGraphs,
                computation.observations(),
                correctiveActionStates
            );
        for (LocationDashboardDerivedGraphSupport.DerivedGraphUpdate derivedGraphUpdate : derivedGraphUpdates) {
            Graph targetGraph = derivedGraphUpdate.graph();
            if (targetGraph == null || targetGraph.getId() == null) {
                continue;
            }
            targetGraph.setLayout(withDerivedImportMetadata(
                targetGraph.getLayout(),
                derivedGraphUpdate.derivedGraphType(),
                strategy.locationName()
            ));
            targetGraph.setData(derivedGraphUpdate.data());
            graphsToPersistById.put(targetGraph.getId(), targetGraph);
        }

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

    private void requireMatchingLocationTitle(String workbookTitle, String locationName, String strategyLocationName) {
        String normalizedWorkbookTitle = normalizeKey(workbookTitle);
        if (normalizedWorkbookTitle == null
            || !Objects.equals(normalizedWorkbookTitle, normalizeKey(locationName))
            || !Objects.equals(normalizedWorkbookTitle, normalizeKey(strategyLocationName))) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_location_title_mismatch",
                "Spreadsheet location title does not match the selected location."
            );
        }
    }

    private Map<String, Graph> matchGraphs(List<GraphConfig> graphDefinitions, List<Graph> assignedGraphs, String locationName) {
        Map<String, Graph> matchedGraphsByDefinitionId = new LinkedHashMap<>();
        Set<Long> reservedGraphIds = new LinkedHashSet<>();

        for (GraphConfig graphDefinition : graphDefinitions) {
            List<Graph> metadataMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> Objects.equals(
                    normalizeKey(readImportMetadata(graph).get("graphId")),
                    normalizeKey(graphDefinition.id())
                ))
                .toList();
            if (metadataMatches.size() > 1) {
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_invalid",
                    "Dashboard graph metadata is ambiguous for " + graphDefinition.title() + "."
                );
            }
            if (metadataMatches.size() == 1) {
                Graph matchedGraph = metadataMatches.getFirst();
                reservedGraphIds.add(matchedGraph.getId());
                matchedGraphsByDefinitionId.put(normalizeKey(graphDefinition.id()), matchedGraph);
                continue;
            }

            List<Graph> titleMatches = assignedGraphs.stream()
                .filter(graph -> !reservedGraphIds.contains(graph.getId()))
                .filter(graph -> Objects.equals(normalizeKey(graph.getName()), normalizeKey(graphDefinition.title())))
                .toList();
            if (titleMatches.isEmpty()) {
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_not_found",
                    "Required dashboard graph was not found for " + locationName + ": " + graphDefinition.title() + "."
                );
            }
            if (titleMatches.size() > 1) {
                throw new ApiClientException(
                    HttpStatus.BAD_REQUEST,
                    "location_dashboard_graph_invalid",
                    "Dashboard graph title is ambiguous for " + graphDefinition.title() + "."
                );
            }
            Graph matchedGraph = titleMatches.getFirst();
            reservedGraphIds.add(matchedGraph.getId());
            matchedGraphsByDefinitionId.put(normalizeKey(graphDefinition.id()), matchedGraph);
        }

        return matchedGraphsByDefinitionId;
    }

    private Map<String, String> readImportMetadata(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return Map.of();
        }
        Object metaValue = graph.getLayout().get("meta");
        if (!(metaValue instanceof Map<?, ?> meta)) {
            return Map.of();
        }
        Object importMetaValue = meta.get(IMPORT_LAYOUT_META_KEY);
        if (!(importMetaValue instanceof Map<?, ?> importMeta)) {
            return Map.of();
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        importMeta.forEach((key, value) -> {
            if (key != null && value != null) {
                metadata.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return Map.copyOf(metadata);
    }

    private Map<String, Object> withImportMetadataAndDefaults(
        Map<String, Object> existingLayout,
        GraphConfig graphDefinition,
        String strategyLocationName
    ) {
        Map<String, Object> layout = copyMutableMap(existingLayout);

        Map<String, Object> meta = copyMutableMap(asMap(layout.get("meta")));
        meta.put(IMPORT_LAYOUT_META_KEY, new LinkedHashMap<>(Map.of(
            "graphId", graphDefinition.id(),
            "graphTitle", graphDefinition.title(),
            "importType", graphDefinition.importType().value(),
            "sublocationKey", graphDefinition.sublocationKey(),
            "locationName", strategyLocationName
        )));
        layout.put("meta", meta);

        Map<String, Object> xAxis = copyMutableMap(asMap(layout.get("xaxis")));
        xAxis.put("type", "date");
        xAxis.putIfAbsent("tickformat", "%b %Y");
        layout.put("xaxis", xAxis);

        Map<String, Object> yAxis = copyMutableMap(asMap(layout.get("yaxis")));
        yAxis.put("range", List.of(0, 100));
        yAxis.put("title", "% Compliance");
        yAxis.put("ticksuffix", "%");
        layout.put("yaxis", yAxis);

        return layout;
    }

    private Map<String, Object> withDerivedImportMetadata(
        Map<String, Object> existingLayout,
        LocationDashboardDerivedGraphSupport.DerivedGraphType derivedGraphType,
        String strategyLocationName
    ) {
        Map<String, Object> layout = copyMutableMap(existingLayout);
        Map<String, Object> meta = copyMutableMap(asMap(layout.get("meta")));
        Map<String, Object> importMeta = copyMutableMap(asMap(meta.get(IMPORT_LAYOUT_META_KEY)));
        importMeta.put("derivedGraphType", LocationDashboardDerivedGraphSupport.metadataValue(derivedGraphType));
        importMeta.put("locationName", strategyLocationName);
        meta.put(IMPORT_LAYOUT_META_KEY, importMeta);
        layout.put("meta", meta);
        return layout;
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return Map.of();
        }
        Map<String, Object> copiedMap = new LinkedHashMap<>();
        mapValue.forEach((key, nestedValue) -> {
            if (key != null) {
                copiedMap.put(String.valueOf(key), nestedValue);
            }
        });
        return copiedMap;
    }

    private Map<String, Object> copyMutableMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String normalizeGraphType(String rawGraphType) {
        if (rawGraphType == null || rawGraphType.isBlank()) {
            return "scatter";
        }
        String normalized = rawGraphType.strip().toLowerCase(Locale.ROOT);
        return "line".equals(normalized) ? "scatter" : normalized;
    }

    private List<LocationDashboardDerivedGraphSupport.CorrectiveActionState> upsertCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        if (correctiveActions.isEmpty()) {
            return List.of();
        }

        List<String> titles = correctiveActions.stream()
            .map(LocationDashboardImportStrategy.CorrectiveActionDraft::title)
            .distinct()
            .toList();
        Map<String, ServiceEvent> existingByTitle = new LinkedHashMap<>();
        for (ServiceEvent existingCorrectiveAction : serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndTitleIn(
            location.getId(),
            titles
        )) {
            existingByTitle.putIfAbsent(normalizeKey(existingCorrectiveAction.getTitle()), existingCorrectiveAction);
        }

        List<ServiceEvent> eventsToPersist = new ArrayList<>();
        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            ServiceEvent serviceEvent = existingByTitle.get(normalizeKey(draft.title()));
            boolean newEvent = serviceEvent == null;
            if (serviceEvent == null) {
                serviceEvent = new ServiceEvent();
                serviceEvent.setLocation(location);
                serviceEvent.setCorrectiveAction(true);
            }
            serviceEvent.setTitle(draft.title());
            serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
            if (newEvent) {
                serviceEvent.setEventDate(draft.observedDate());
                serviceEvent.setEventTime(ALL_DAY_START_TIME);
                serviceEvent.setEndEventDate(draft.observedDate());
                serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
                serviceEvent.setStatus(resolveImportedCorrectiveActionStatus(draft.observedDate()));
            }
            serviceEvent.setDescription(draft.description());
            eventsToPersist.add(serviceEvent);
        }

        List<ServiceEvent> persistedEvents = serviceEventRepository.saveAllAndFlush(eventsToPersist);
        Map<String, ServiceEvent> persistedByTitle = persistedEvents.stream()
            .filter(serviceEvent -> serviceEvent.getTitle() != null)
            .collect(Collectors.toMap(
                serviceEvent -> normalizeKey(serviceEvent.getTitle()),
                serviceEvent -> serviceEvent,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        return correctiveActions.stream()
            .map(draft -> new LocationDashboardDerivedGraphSupport.CorrectiveActionState(
                draft,
                persistedByTitle.get(normalizeKey(draft.title()))
            ))
            .toList();
    }

    private ServiceEventStatus resolveImportedCorrectiveActionStatus(LocalDate observedDate) {
        LocalDate today = LocalDate.now(clock);
        if (observedDate.isBefore(today)) {
            return ServiceEventStatus.OVERDUE;
        }
        if (observedDate.isEqual(today)) {
            return ServiceEventStatus.CURRENT;
        }
        return ServiceEventStatus.UPCOMING;
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

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
