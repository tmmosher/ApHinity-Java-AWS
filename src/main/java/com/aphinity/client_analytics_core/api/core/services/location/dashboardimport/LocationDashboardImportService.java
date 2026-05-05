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
        List<Long> targetGraphIds = matchedGraphsByDefinitionId.values().stream()
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
        Map<String, GraphConfig> graphDefinitionsById = strategy.graphDefinitions().stream()
            .collect(Collectors.toMap(
                graphDefinition -> normalizeKey(graphDefinition.id()),
                graphDefinition -> graphDefinition,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<Graph> graphsToPersist = new ArrayList<>();
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
            graphsToPersist.add(lockedGraph);
        }

        try {
            graphRepository.saveAllAndFlush(graphsToPersist);
            upsertCorrectiveActions(location, computation.correctiveActions());
            List<GraphResponse> responses = buildResponsesInDefinitionOrder(strategy.graphDefinitions(), matchedGraphsByDefinitionId, lockedGraphsById);
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

    private void upsertCorrectiveActions(
        Location location,
        List<LocationDashboardImportStrategy.CorrectiveActionDraft> correctiveActions
    ) {
        if (correctiveActions.isEmpty()) {
            return;
        }

        List<LocalDate> eventDates = correctiveActions.stream()
            .map(LocationDashboardImportStrategy.CorrectiveActionDraft::eventDate)
            .distinct()
            .toList();
        Map<String, ServiceEvent> existingByIdentity = new LinkedHashMap<>();
        for (ServiceEvent existingCorrectiveAction : serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndEventDateIn(
            location.getId(),
            eventDates
        )) {
            existingByIdentity.putIfAbsent(identity(existingCorrectiveAction.getEventDate(), existingCorrectiveAction.getTitle()), existingCorrectiveAction);
        }

        List<ServiceEvent> eventsToPersist = new ArrayList<>();
        for (LocationDashboardImportStrategy.CorrectiveActionDraft draft : correctiveActions) {
            ServiceEvent serviceEvent = existingByIdentity.get(identity(draft.eventDate(), draft.title()));
            if (serviceEvent == null) {
                serviceEvent = new ServiceEvent();
                serviceEvent.setLocation(location);
                serviceEvent.setCorrectiveAction(true);
            }
            serviceEvent.setTitle(draft.title());
            serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
            serviceEvent.setEventDate(draft.eventDate());
            serviceEvent.setEventTime(ALL_DAY_START_TIME);
            serviceEvent.setEndEventDate(draft.eventDate());
            serviceEvent.setEndEventTime(ALL_DAY_END_TIME);
            serviceEvent.setDescription(draft.description());
            serviceEvent.setStatus(resolveCorrectiveActionStatus(draft.eventDate()));
            eventsToPersist.add(serviceEvent);
        }

        serviceEventRepository.saveAllAndFlush(eventsToPersist);
    }

    private ServiceEventStatus resolveCorrectiveActionStatus(LocalDate eventDate) {
        LocalDate today = LocalDate.now(clock);
        if (eventDate.isBefore(today)) {
            return ServiceEventStatus.COMPLETED;
        }
        if (eventDate.isEqual(today)) {
            return ServiceEventStatus.CURRENT;
        }
        return ServiceEventStatus.UPCOMING;
    }

    private String identity(LocalDate eventDate, String title) {
        return eventDate + "|" + normalizeKey(title);
    }

    private List<GraphResponse> buildResponsesInDefinitionOrder(
        List<GraphConfig> graphDefinitions,
        Map<String, Graph> matchedGraphsByDefinitionId,
        Map<Long, Graph> lockedGraphsById
    ) {
        List<GraphResponse> responses = new ArrayList<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            Graph matchedGraph = matchedGraphsByDefinitionId.get(normalizeKey(graphDefinition.id()));
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph lockedGraph = lockedGraphsById.get(matchedGraph.getId());
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
