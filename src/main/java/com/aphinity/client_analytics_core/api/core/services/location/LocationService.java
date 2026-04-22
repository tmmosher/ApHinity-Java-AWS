package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUserId;
import com.aphinity.client_analytics_core.api.core.entities.location.UserSubscriptionToLocation;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateRequest;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphNameUpdateResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationGraphTemplateFactory.GraphTemplate;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Business logic for location visibility and membership administration.
 * This whole file has sorta exploded out of control.
 *  TODO split this up into multiple services perhaps as an accessor / operator service pair?
 */
@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final GraphRepository graphRepository;
    private final LocationGraphRepository locationGraphRepository;
    private final LocationUserRepository locationUserRepository;
    private final UserSubscriptionToLocationRepository userSubscriptionToLocationRepository;
    private final AccountRoleService accountRoleService;
    private final LocationGraphTemplateFactory locationGraphTemplateFactory;
    private final LocationGraphUpdatePayloadValidationFactory locationGraphUpdatePayloadValidationFactory;

    public LocationService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        GraphRepository graphRepository,
        LocationGraphRepository locationGraphRepository,
        LocationUserRepository locationUserRepository,
        UserSubscriptionToLocationRepository userSubscriptionToLocationRepository,
        AccountRoleService accountRoleService,
        LocationGraphTemplateFactory locationGraphTemplateFactory,
        LocationGraphUpdatePayloadValidationFactory locationGraphUpdatePayloadValidationFactory
    ) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.graphRepository = graphRepository;
        this.locationGraphRepository = locationGraphRepository;
        this.locationUserRepository = locationUserRepository;
        this.userSubscriptionToLocationRepository = userSubscriptionToLocationRepository;
        this.accountRoleService = accountRoleService;
        this.locationGraphTemplateFactory = locationGraphTemplateFactory;
        this.locationGraphUpdatePayloadValidationFactory = locationGraphUpdatePayloadValidationFactory;
    }

    /**
     * Returns all locations visible to the user.
     * Partners/admins can view every location; client users only see locations where they
     * have membership.
     *
     * @param userId authenticated user id
     * @return accessible locations
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getAccessibleLocations(Long userId) {
        AppUser user = requireUser(userId);
        if (accountRoleService.isPartnerOrAdmin(user)) {
            List<Location> response = locationRepository.findAllByOrderByNameAsc();
            return response.stream()
                    .map(location -> toLocationResponse(location, null))
                    .toList();
        }

        Map<Long, LocationResponse> uniqueLocations = new LinkedHashMap<>();
        // Defensive de-duplication protects response quality if joins return repeated rows.
        for (LocationUser membership : locationUserRepository.findByUserIdWithLocation(userId)) {
            Location location = membership.getLocation();
            if (location == null || location.getId() == null) {
                continue;
            }
            uniqueLocations.putIfAbsent(location.getId(), toLocationResponse(location, null));
        }
        return List.copyOf(uniqueLocations.values());
    }

    /**
     * Returns one location if the user has access to it.
     *
     * @param userId authenticated user id
     * @param locationId target location id
     * @return location payload
     */
    @Transactional(readOnly = true)
    public LocationResponse getAccessibleLocation(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }
        return toLocationResponse(location, user);
    }

    /**
     * Returns graphs assigned to a location when the caller has access.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return assigned graph payloads
     */
    @Transactional(readOnly = true)
    public List<GraphResponse> getAccessibleLocationGraphs(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }

        return locationGraphRepository.findByLocationIdWithGraph(locationId).stream()
            .map(LocationGraph::getGraph)
            .map(this::toGraphResponse)
            .toList();
    }

    /**
     * Creates a graph, links it to the location, and appends it to the selected dashboard section.
     * Only partner/admin callers may create new graphs.
     *
     * @param userId authenticated user id performing the create
     * @param locationId target location id
     * @param sectionId dashboard section identifier receiving the new graph
     * @param graphType requested graph template type
     * @return created graph payload
     */
    @Transactional
    public GraphResponse createLocationGraph(
        Long userId,
        Long locationId,
        Long sectionId,
        boolean createNewSection,
        String graphType
    ) {
        AppUser user = requireUser(userId);
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            log.warn(
                "Rejected graph create due to insufficient permissions actorUserId={} locationId={} sectionId={} createNewSection={} graphType={}",
                userId,
                locationId,
                sectionId,
                createNewSection,
                graphType
            );
            throw forbidden();
        }

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        Long targetSectionId = resolveTargetSectionId(location.getSectionLayout(), sectionId, createNewSection);
        GraphTemplate template;
        try {
            template = locationGraphTemplateFactory.create(graphType, location.getName());
        } catch (IllegalArgumentException ex) {
            throw invalidGraphType();
        }

        Graph graph = new Graph();
        graph.setName(template.name());
        graph.setLayout(template.layout());
        graph.setConfig(template.config());
        graph.setStyle(template.style());
        graph.setData(template.data());

        Graph savedGraph;
        try {
            savedGraph = graphRepository.saveAndFlush(graph);
        } catch (RuntimeException ex) {
            log.error(
                "Graph create persistence failed before assignment actorUserId={} locationId={} sectionId={} createNewSection={} graphType={}",
                userId,
                locationId,
                targetSectionId,
                createNewSection,
                graphType,
                ex
            );
            throw ex;
        }

        Long graphId = savedGraph.getId();
        if (graphId == null) {
            throw new IllegalStateException("Created graph id was null");
        }

        savedGraph = refreshGraphFromStore(graphId, savedGraph);

        location.setSectionLayout(
            appendGraphToSectionLayout(location.getSectionLayout(), targetSectionId, createNewSection, graphId)
        );

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(locationId, graphId));
        locationGraph.setLocation(location);
        locationGraph.setGraph(savedGraph);

        try {
            locationGraphRepository.save(locationGraph);
            locationRepository.saveAndFlush(location);
        } catch (RuntimeException ex) {
            log.error(
                "Graph assignment persistence failed actorUserId={} locationId={} graphId={} sectionId={} createNewSection={} graphType={}",
                userId,
                locationId,
                graphId,
                targetSectionId,
                createNewSection,
                graphType,
                ex
            );
            throw ex;
        }

        return toGraphResponse(savedGraph);
    }

    /**
     * Deletes a graph assigned to a location and removes its section layout reference.
     * Only partner/admin callers may delete location graphs.
     *
     * @param userId authenticated user id performing the delete
     * @param locationId target location id
     * @param graphId target graph id
     */
    @Transactional
    public void deleteLocationGraph(Long userId, Long locationId, Long graphId) {
        AppUser user = requireUser(userId);
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            log.warn(
                "Rejected graph delete due to insufficient permissions actorUserId={} locationId={} graphId={}",
                userId,
                locationId,
                graphId
            );
            throw forbidden();
        }

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        Graph graph = graphRepository.findByLocationIdAndGraphIdForUpdate(locationId, graphId)
            .orElseThrow(this::locationGraphNotFound);

        location.setSectionLayout(removeGraphFromSectionLayout(location.getSectionLayout(), graphId));

        try {
            locationGraphRepository.deleteById(new LocationGraphId(locationId, graphId));
            locationGraphRepository.flush();
            if (locationGraphRepository.findByIdGraphId(graphId).isEmpty()) {
                graphRepository.delete(graph);
                graphRepository.flush();
            }
            locationRepository.saveAndFlush(location);
        } catch (RuntimeException ex) {
            log.error(
                "Graph delete persistence failed actorUserId={} locationId={} graphId={}",
                userId,
                locationId,
                graphId,
                ex
            );
            throw ex;
        }

        log.info(
            "Deleted location graph locationId={} graphId={} actorUserId={}",
            locationId,
            graphId,
            userId
        );
    }

    /**
     * Replaces graph trace data (and optional layout) for one or more graphs linked to a location.
     * Only partner/admin callers may mutate graph payloads.
     *
     * @param userId authenticated user id performing the update
     * @param locationId target location id
     * @param graphUpdates requested graph payload replacements
     */
    @Transactional
    public void updateLocationGraphData(
        Long userId,
        Long locationId,
        List<LocationGraphDataUpdateRequest> graphUpdates
    ) {
        AppUser user = requireUser(userId);
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            log.warn(
                "Rejected graph update due to insufficient permissions actorUserId={} locationId={}",
                userId,
                locationId
            );
            throw forbidden();
        }
        if (!locationRepository.existsById(locationId)) {
            log.warn(
                "Rejected graph update because location was not found actorUserId={} locationId={}",
                userId,
                locationId
            );
            throw locationNotFound();
        }

        if (graphUpdates == null || graphUpdates.isEmpty()) {
            log.info(
                "Skipping graph update because request payload was empty actorUserId={} locationId={}",
                userId,
                locationId
            );
            return;
        }

        Map<Long, LocationGraphDataUpdateRequest> updatesByGraphId = mapGraphUpdatesById(
            graphUpdates,
            locationId,
            userId
        );
        List<Graph> graphs = graphRepository.findByLocationIdAndGraphIdInForUpdate(
            locationId,
            updatesByGraphId.keySet()
        );
        if (graphs.size() != updatesByGraphId.size()) {
            List<Long> matchedGraphIds = graphs.stream().map(Graph::getId).sorted().toList();
            log.warn(
                "Rejected graph update because one or more graphs were not assigned to location actorUserId={} locationId={} requestedGraphIds={} matchedGraphIds={}",
                userId,
                locationId,
                updatesByGraphId.keySet(),
                matchedGraphIds
            );
            throw locationGraphNotFound();
        }

        Map<Long, ValidatedGraphPayload> validatedPayloads = new LinkedHashMap<>();
        for (Graph graph : graphs) {
            LocationGraphDataUpdateRequest update = updatesByGraphId.get(graph.getId());
            try {
                validateExpectedGraphTimestamp(update, graph, locationId, userId);
                validatedPayloads.put(
                    graph.getId(),
                    locationGraphUpdatePayloadValidationFactory.validateForUpdate(
                        graph.getData(),
                        update.data(),
                        update.layout()
                    )
                );
            } catch (IllegalArgumentException ex) {
                log.warn(
                    "Rejected invalid graph data update locationId={} graphId={} actorUserId={}",
                    locationId,
                    graph.getId(),
                    userId,
                    ex
                );
                throw invalidGraphData();
            }
        }

        for (Graph graph : graphs) {
            ValidatedGraphPayload validatedPayload = validatedPayloads.get(graph.getId());
            if (validatedPayload == null) {
                throw new IllegalStateException("Validated graph payload was missing");
            }
            LocationGraphDataUpdateRequest update = updatesByGraphId.get(graph.getId());
            if (update.layout() != null) {
                graph.setLayout(validatedPayload.layout());
            }
            graph.setData(validatedPayload.data());
        }

        try {
            graphRepository.saveAll(graphs);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
        } catch (RuntimeException ex) {
            log.error(
                "Graph update persistence failed actorUserId={} locationId={} graphIds={}",
                userId,
                locationId,
                updatesByGraphId.keySet(),
                ex
            );
            throw ex;
        }
        log.info(
            "Updated graph data payloads locationId={} graphCount={} actorUserId={} graphIds={}",
            locationId,
            graphs.size(),
            userId,
            updatesByGraphId.keySet()
        );
    }

    /**
     * Renames a single graph assigned to a location.
     * Only partner/admin callers may mutate graph names.
     *
     * @param userId authenticated user id performing the update
     * @param locationId target location id
     * @param graphId target graph id
     * @param name desired graph display name
     * @return persisted graph name metadata
     */
    @Transactional
    public GraphNameUpdateResponse updateLocationGraphName(
        Long userId,
        Long locationId,
        Long graphId,
        String name
    ) {
        AppUser user = requireUser(userId);
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            log.warn(
                "Rejected graph rename due to insufficient permissions actorUserId={} locationId={} graphId={}",
                userId,
                locationId,
                graphId
            );
            throw forbidden();
        }
        if (!locationRepository.existsById(locationId)) {
            log.warn(
                "Rejected graph rename because location was not found actorUserId={} locationId={} graphId={}",
                userId,
                locationId,
                graphId
            );
            throw locationNotFound();
        }

        Graph graph = graphRepository.findByLocationIdAndGraphIdForUpdate(locationId, graphId)
            .orElseThrow(this::locationGraphNotFound);
        graph.setName(normalizeGraphName(name));

        try {
            Graph savedGraph = graphRepository.saveAndFlush(graph);
            savedGraph = refreshGraphFromStore(graphId, savedGraph);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return new GraphNameUpdateResponse(
                savedGraph.getId(),
                savedGraph.getName(),
                savedGraph.getUpdatedAt()
            );
        } catch (RuntimeException ex) {
            log.error(
                "Graph rename persistence failed actorUserId={} locationId={} graphId={}",
                userId,
                locationId,
                graphId,
                ex
            );
            throw ex;
        }
    }

    /**
     * Renames a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @param name desired location name
     * @return updated location payload
     */
    @Transactional
    public LocationResponse updateLocationName(Long userId, Long locationId, String name) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);

        String normalizedName = normalizeLocationName(name);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        location.setName(normalizedName);

        try {
            locationRepository.saveAndFlush(location);
        } catch (DataIntegrityViolationException ex) {
            throw locationNameInUse();
        }

        return toLocationResponse(location, user);
    }

    /**
     * Updates the work-order submission email associated with a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @param workOrderEmail work-order submission email or null to clear it
     * @return updated location payload
     */
    @Transactional
    public LocationResponse updateLocationWorkOrderEmail(Long userId, Long locationId, String workOrderEmail) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        location.setWorkOrderEmail(normalizeWorkOrderEmail(workOrderEmail));

        try {
            locationRepository.saveAndFlush(location);
        } catch (RuntimeException ex) {
            log.error(
                "Location work-order email persistence failed actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }

        return toLocationResponse(location, user);
    }

    /**
     * Subscribes a verified user to location alerts.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return updated location payload
     */
    @Transactional
    public LocationResponse subscribeToLocationAlerts(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }

        UserSubscriptionToLocation subscription = userSubscriptionToLocationRepository
            .findByLocationIdAndUserId(locationId, userId)
            .orElseGet(() -> {
                UserSubscriptionToLocation toCreate = new UserSubscriptionToLocation();
                toCreate.setLocation(location);
                toCreate.setUserEmail(user);
                return toCreate;
            });
        subscription.setLocation(location);
        subscription.setUserEmail(user);

        try {
            userSubscriptionToLocationRepository.save(subscription);
        } catch (RuntimeException ex) {
            log.error(
                "Location alert subscription persistence failed actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }

        return toLocationResponse(location, user);
    }

    /**
     * Unsubscribes a verified user from location alerts.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return updated location payload
     */
    @Transactional
    public LocationResponse unsubscribeFromLocationAlerts(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }

        userSubscriptionToLocationRepository.findByLocationIdAndUserId(locationId, userId)
            .ifPresent(userSubscriptionToLocationRepository::delete);

        return toLocationResponse(location, user);
    }

    /**
     * Creates a new location.
     *
     * @param userId authenticated user id
     * @param name desired location name
     * @return created location payload
     */
    @Transactional
    public LocationResponse createLocation(Long userId, String name) {
        AppUser user = requireUser(userId);
        requireAdmin(user);

        Location location = new Location();
        location.setName(normalizeLocationName(name));

        try {
            locationRepository.saveAndFlush(location);
        } catch (DataIntegrityViolationException ex) {
            throw locationNameInUse();
        }

        return toLocationResponse(location, user);
    }

    /**
     * Returns memberships for a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return location memberships
     */
    @Transactional(readOnly = true)
    public List<LocationMembershipResponse> getLocationMemberships(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }

        return locationUserRepository.findByLocationIdWithUser(locationId).stream()
            .map(this::toLocationMembershipResponse)
            .toList();
    }

    /**
     * Ensures membership exists for a target user at a location.
     *
     * @param userId authenticated user id performing the change
     * @param locationId target location id
     * @param targetUserId target user id
     * @return persisted membership payload
     */
    @Transactional
    public LocationMembershipResponse upsertLocationMembership(
        Long userId,
        Long locationId,
        Long targetUserId
    ) {
        AppUser actingUser = requireUser(userId);
        requirePartnerOrAdmin(actingUser);

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        AppUser targetUser = appUserRepository.findById(targetUserId).orElseThrow(this::targetUserNotFound);

        // Upsert semantics: reuse existing membership when present, otherwise create a new one.
        LocationUser membership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, targetUserId)
            .orElseGet(() -> {
                LocationUser toCreate = new LocationUser();
                toCreate.setId(new LocationUserId(locationId, targetUserId));
                toCreate.setLocation(location);
                toCreate.setUser(targetUser);
                return toCreate;
            });

        membership.setLocation(location);
        membership.setUser(targetUser);

        LocationUser persisted = locationUserRepository.save(membership);
        return toLocationMembershipResponse(persisted);
    }

    /**
     * Deletes a membership for a target user at a location.
     *
     * @param authenticatedUserId authenticated user id performing the change
     * @param locationId target location id
     * @param targetUserId target user id
     */
    @Transactional
    public void deleteLocationMembership(
        Long authenticatedUserId,
        Long locationId,
        Long targetUserId
    ) {
        AppUser actingUser = requireUser(authenticatedUserId);
        if (!accountRoleService.isPartnerOrAdmin(actingUser)) {
            log.warn(
                "Rejected location membership delete due to insufficient permissions actorUserId={} locationId={} targetUserId={}",
                authenticatedUserId,
                locationId,
                targetUserId
            );
            throw forbidden();
        }

        // Fast path: a successful delete only needs membership lookup + delete.
        LocationUser membership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, targetUserId)
            .orElse(null);
        if (membership != null) {
            try {
                locationUserRepository.delete(membership);
            } catch (RuntimeException ex) {
                log.error(
                    "Location membership delete persistence failed actorUserId={} locationId={} targetUserId={}",
                    authenticatedUserId,
                    locationId,
                    targetUserId,
                    ex
                );
                throw ex;
            }
            log.info(
                "Deleted location membership locationId={} targetUserId={} actorUserId={}",
                locationId,
                targetUserId,
                authenticatedUserId
            );
            return;
        }

        // Preserve explicit 404 reasons for clients when membership is absent.
        if (!locationRepository.existsById(locationId)) {
            log.warn(
                "Rejected location membership delete because location was not found actorUserId={} locationId={} targetUserId={}",
                authenticatedUserId,
                locationId,
                targetUserId
            );
            throw locationNotFound();
        }
        if (!appUserRepository.existsById(targetUserId)) {
            log.warn(
                "Rejected location membership delete because target user was not found actorUserId={} locationId={} targetUserId={}",
                authenticatedUserId,
                locationId,
                targetUserId
            );
            throw targetUserNotFound();
        }
        log.warn(
            "Rejected location membership delete because membership was not found actorUserId={} locationId={} targetUserId={}",
            authenticatedUserId,
            locationId,
            targetUserId
        );
        throw locationMembershipNotFound();
    }

    /**
     * Indicates whether a user can access a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return {@code true} when access is allowed
     */
    @Transactional(readOnly = true)
    public boolean isUserAllowedToAccessLocation(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        return hasLocationAccess(user, locationId);
    }

    /**
     * Checks access based on account-level role and direct membership.
     */
    private boolean hasLocationAccess(AppUser user, Long locationId) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return true;
        }
        return locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, user.getId());
    }

    /**
     * Maps membership entities into API response shape.
     */
    private LocationMembershipResponse toLocationMembershipResponse(LocationUser membership) {
        AppUser member = membership.getUser();
        String userEmail = member == null ? null : member.getEmail();
        return new LocationMembershipResponse(
            membership.getId().getLocationId(),
            membership.getId().getUserId(),
            userEmail,
            membership.getCreatedAt()
        );
    }

    /**
     * Maps location entities into API response shape.
     */
    private LocationResponse toLocationResponse(Location location) {
        return toLocationResponse(location, null);
    }

    private LocationResponse toLocationResponse(Location location, AppUser user) {
        Boolean alertsSubscribed = null;
        if (user != null) {
            alertsSubscribed = userSubscriptionToLocationRepository.existsByLocationIdAndUserId(location.getId(), user.getId());
        }
        return new LocationResponse(
            location.getId(),
            location.getName(),
            location.getCreatedAt(),
            location.getUpdatedAt(),
            location.getSectionLayout(),
            location.getWorkOrderEmail(),
            alertsSubscribed
        );
    }

    /**
     * Maps graph entities into API response shape.
     */
    private GraphResponse toGraphResponse(Graph graph) {
        GraphPayloadMapper.GraphPayload payload;
        try {
            payload = GraphPayloadMapper.normalize(
                graph.getData(),
                graph.getLayout(),
                graph.getConfig(),
                graph.getStyle()
            );
        } catch (IllegalArgumentException ex) {
            log.warn(
                "Invalid graph payload for graphId={} during location graph response mapping",
                graph.getId(),
                ex
            );
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Graph payload is invalid");
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

    private Long resolveTargetSectionId(
        Map<String, Object> sectionLayout,
        Long sectionId,
        boolean createNewSection
    ) {
        if (createNewSection) {
            return nextSectionId(sectionLayout);
        }

        requireExistingSection(sectionLayout, sectionId);
        return sectionId;
    }

    private Long nextSectionId(Map<String, Object> sectionLayout) {
        long maxSectionId = 0L;
        for (Object sectionValue : readSectionList(sectionLayout)) {
            if (!(sectionValue instanceof Map<?, ?> sectionMap)) {
                continue;
            }
            Object rawSectionId = sectionMap.get("section_id");
            if (rawSectionId instanceof Number sectionNumber) {
                maxSectionId = Math.max(maxSectionId, sectionNumber.longValue());
            }
        }
        return maxSectionId + 1;
    }

    private Map<String, Object> appendGraphToSectionLayout(
        Map<String, Object> sectionLayout,
        Long sectionId,
        boolean createNewSection,
        Long graphId
    ) {
        Map<String, Object> nextLayout = sectionLayout == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(sectionLayout);
        List<Object> sections = readSectionList(sectionLayout);

        boolean matchedSection = false;
        List<Object> nextSections = new ArrayList<>(sections.size());
        for (Object sectionValue : sections) {
            if (!(sectionValue instanceof Map<?, ?> sectionMap)) {
                nextSections.add(sectionValue);
                continue;
            }

            Map<String, Object> nextSection = copyObjectMap(sectionMap);
            if (matchesSectionId(nextSection.get("section_id"), sectionId)) {
                List<Object> graphIds = copyGraphIdList(nextSection.get("graph_ids"));
                graphIds.add(graphId);
                nextSection.put("graph_ids", List.copyOf(graphIds));
                matchedSection = true;
            }
            nextSections.add(nextSection);
        }

        if (!matchedSection) {
            if (!createNewSection) {
                throw locationSectionNotFound();
            }
            nextSections.add(Map.of(
                "section_id", sectionId,
                "graph_ids", List.of(graphId)
            ));
        }

        nextLayout.put("sections", List.copyOf(nextSections));
        return nextLayout;
    }

    private Map<String, Object> removeGraphFromSectionLayout(
        Map<String, Object> sectionLayout,
        Long graphId
    ) {
        Map<String, Object> nextLayout = sectionLayout == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(sectionLayout);
        List<Object> sections = readSectionList(sectionLayout);
        List<Object> nextSections = new ArrayList<>(sections.size());

        for (Object sectionValue : sections) {
            if (!(sectionValue instanceof Map<?, ?> sectionMap)) {
                nextSections.add(sectionValue);
                continue;
            }

            Map<String, Object> nextSection = copyObjectMap(sectionMap);
            List<Object> retainedGraphIds = new ArrayList<>();
            for (Object rawGraphId : copyGraphIdList(nextSection.get("graph_ids"))) {
                if (!matchesGraphId(rawGraphId, graphId)) {
                    retainedGraphIds.add(rawGraphId);
                }
            }
            nextSection.put("graph_ids", List.copyOf(retainedGraphIds));
            nextSections.add(nextSection);
        }

        nextLayout.put("sections", List.copyOf(nextSections));
        return nextLayout;
    }

    private void requireExistingSection(Map<String, Object> sectionLayout, Long sectionId) {
        if (sectionId == null) {
            throw locationSectionNotFound();
        }

        boolean matchedSection = readSectionList(sectionLayout).stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .anyMatch(section -> matchesSectionId(section.get("section_id"), sectionId));

        if (!matchedSection) {
            throw locationSectionNotFound();
        }
    }

    private List<Object> readSectionList(Map<String, Object> sectionLayout) {
        if (sectionLayout == null) {
            return List.of();
        }

        Object sectionsValue = sectionLayout.get("sections");
        if (!(sectionsValue instanceof List<?> sections)) {
            return List.of();
        }

        return new ArrayList<>(sections);
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }

    private boolean matchesSectionId(Object rawSectionId, Long expectedSectionId) {
        return rawSectionId instanceof Number sectionNumber
            && expectedSectionId != null
            && sectionNumber.longValue() == expectedSectionId;
    }

    private boolean matchesGraphId(Object rawGraphId, Long expectedGraphId) {
        return rawGraphId instanceof Number graphNumber
            && expectedGraphId != null
            && graphNumber.longValue() == expectedGraphId;
    }

    private List<Object> copyGraphIdList(Object rawGraphIds) {
        if (!(rawGraphIds instanceof List<?> graphIds)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(graphIds);
    }

    /**
     * Trims and validates location names.
     */
    private String normalizeLocationName(String value) {
        if (value == null) {
            throw invalidLocationName();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidLocationName();
        }
        return normalized;
    }

    private String normalizeGraphName(String value) {
        if (value == null) {
            throw invalidGraphName();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidGraphName();
        }
        return normalized;
    }

    private String normalizeWorkOrderEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * Loads the authenticated user or fails with a standard unauthorized error.
     */
    private AppUser requireUser(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);
        return user;
    }

    /**
     * Enforces elevated role requirements for administrative location operations.
     */
    private void requirePartnerOrAdmin(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw forbidden();
        }
    }

    private void requireAdmin(AppUser user) {
        if (accountRoleService.resolveAccountRole(user) != AccountRole.ADMIN) {
            throw forbidden();
        }
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private void requireVerified(AppUser user) {
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
    }

    private ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }

    private ResponseStatusException targetUserNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
    }

    private ResponseStatusException locationMembershipNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location membership not found");
    }

    private ResponseStatusException locationGraphNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location graph not found");
    }

    private ResponseStatusException invalidLocationName() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location name is required");
    }

    private ResponseStatusException locationNameInUse() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Location name already in use");
    }

    private ResponseStatusException invalidGraphData() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph data is invalid");
    }

    private ResponseStatusException invalidGraphName() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph name is required");
    }

    private ResponseStatusException invalidGraphType() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph type is invalid");
    }

    private ResponseStatusException locationSectionNotFound() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location section not found");
    }

    private ResponseStatusException duplicateGraphUpdates() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graph update list contains duplicate graph ids");
    }

    private ResponseStatusException graphUpdateConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Graph update conflict");
    }

    private Graph refreshGraphFromStore(Long graphId, Graph fallbackGraph) {
        return graphRepository.findById(graphId).orElse(fallbackGraph);
    }

    private Map<Long, LocationGraphDataUpdateRequest> mapGraphUpdatesById(
        List<LocationGraphDataUpdateRequest> graphUpdates,
        Long locationId,
        Long actorUserId
    ) {
        Map<Long, LocationGraphDataUpdateRequest> updatesById = new LinkedHashMap<>();
        for (int index = 0; index < graphUpdates.size(); index++) {
            LocationGraphDataUpdateRequest update = graphUpdates.get(index);
            if (update == null || update.graphId() == null) {
                log.warn(
                    "Rejected graph update row because graph id was missing actorUserId={} locationId={} rowIndex={}",
                    actorUserId,
                    locationId,
                    index
                );
                throw invalidGraphData();
            }
            if (update.data() == null) {
                log.warn(
                    "Rejected graph update row because graph data payload was null actorUserId={} locationId={} graphId={} rowIndex={}",
                    actorUserId,
                    locationId,
                    update.graphId(),
                    index
                );
                throw invalidGraphData();
            }
            try {
                GraphPayloadMapper.toTraceList(update.data());
            } catch (IllegalArgumentException ex) {
                log.warn(
                    "Rejected graph update row because graph data payload was invalid actorUserId={} locationId={} graphId={} rowIndex={}",
                    actorUserId,
                    locationId,
                    update.graphId(),
                    index,
                    ex
                );
                throw invalidGraphData();
            }
            if (update.layout() != null && !(update.layout() instanceof Map<?, ?>)) {
                log.warn(
                    "Rejected graph update row because layout payload was not an object actorUserId={} locationId={} graphId={} rowIndex={}",
                    actorUserId,
                    locationId,
                    update.graphId(),
                    index
                );
                throw invalidGraphData();
            }
            if (updatesById.putIfAbsent(update.graphId(), update) != null) {
                log.warn(
                    "Rejected graph update payload due to duplicate graph id actorUserId={} locationId={} duplicateGraphId={} rowIndex={}",
                    actorUserId,
                    locationId,
                    update.graphId(),
                    index
                );
                throw duplicateGraphUpdates();
            }
        }
        return Map.copyOf(updatesById);
    }

    private void validateExpectedGraphTimestamp(
        LocationGraphDataUpdateRequest update,
        Graph graph,
        Long locationId,
        Long actorUserId
    ) {
        String expectedUpdatedAt = update.expectedUpdatedAt();
        if (expectedUpdatedAt == null) {
            return;
        }

        Instant expectedTimestamp;
        try {
            expectedTimestamp = Instant.parse(expectedUpdatedAt);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Graph expectedUpdatedAt must be an ISO-8601 timestamp", ex);
        }

        Instant actualTimestamp = graph.getUpdatedAt();
        if (!Objects.equals(actualTimestamp, expectedTimestamp)) {
            log.warn(
                "Rejected graph update due to stale graph version actorUserId={} locationId={} graphId={} expectedUpdatedAt={} actualUpdatedAt={}",
                actorUserId,
                locationId,
                graph.getId(),
                expectedUpdatedAt,
                actualTimestamp
            );
            throw graphUpdateConflict();
        }
    }

}
