package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.LocationGraphDataUpdateBatchRequest;
import com.aphinity.client_analytics_core.api.core.requests.LocationRequest;
import com.aphinity.client_analytics_core.api.core.response.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Location and membership endpoints for authenticated users.
 * The controller resolves the authenticated user id from JWT and delegates all authorization
 * and business logic to {@link LocationService}.
 */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationController {
    private static final Logger log = LoggerFactory.getLogger(LocationController.class);

    private final LocationService locationService;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationController(LocationService locationService, AuthenticatedUserService authenticatedUserService) {
        this.locationService = locationService;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Lists locations accessible to the authenticated user.
     *
     * @param jwt authenticated principal JWT
     * @return ordered list of accessible locations
     */
    @GetMapping("/locations")
    public List<LocationResponse> locations(@AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.getAccessibleLocations(userId);
    }

    /**
     * Returns a single location if the authenticated user is allowed to access it.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @return location payload
     */
    @GetMapping("/locations/{locationId}")
    public LocationResponse location(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.getAccessibleLocation(userId, locationId);
    }

    /**
     * Returns graphs assigned to a location if the authenticated user can access it.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @return assigned graph payloads
     */
    @GetMapping("/locations/{locationId}/graphs")
    public List<GraphResponse> locationGraphs(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.getAccessibleLocationGraphs(userId, locationId);
    }

    /**
     * Updates graph trace data for a location.
     * Only partner/admin users are authorized to mutate graph payloads.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @param request validated request containing graph ids and replacement data payloads
     */
    @PutMapping("/locations/{locationId}/graphs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocationGraphData(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody LocationGraphDataUpdateBatchRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        int requestedGraphCount = request.graphs() == null ? 0 : request.graphs().size();
        log.info(
            "Received location graph update request actorUserId={} locationId={} graphCount={}",
            userId,
            locationId,
            requestedGraphCount
        );
        try {
            locationService.updateLocationGraphData(userId, locationId, request.graphs());
            log.info(
                "Completed location graph update request actorUserId={} locationId={} graphCount={}",
                userId,
                locationId,
                requestedGraphCount
            );
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location graph update request actorUserId={} locationId={} graphCount={} status={} reason={}",
                userId,
                locationId,
                requestedGraphCount,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location graph update request actorUserId={} locationId={} graphCount={}",
                userId,
                locationId,
                requestedGraphCount,
                ex
            );
            throw ex;
        }
    }

    /**
     * Updates a location name.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @param request validated request containing new location name
     * @return updated location payload
     */
    @PutMapping("/locations/{locationId}")
    public LocationResponse updateLocation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody LocationRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.updateLocationName(userId, locationId, request.name());
    }

    /**
     * Lists memberships for a location.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @return memberships for the location
     */
    @GetMapping("/locations/{locationId}/memberships")
    public List<LocationMembershipResponse> memberships(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.getLocationMemberships(userId, locationId);
    }

    /**
     * Ensures a user has membership in a location.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @param userId target user identifier
     * @return updated membership payload
     */
    @PutMapping("/locations/{locationId}/memberships/{userId}")
    public LocationMembershipResponse updateMembership(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long userId
    ) {
        Long authenticatedUserId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.upsertLocationMembership(
            authenticatedUserId,
            locationId,
            userId
        );
    }

    /**
     * Deletes a location membership for a target user.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @param targetUserId target user identifier
     */
    @DeleteMapping("/locations/{locationId}/memberships/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMembership(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable("userId") Long targetUserId
    ) {
        Long authenticatedUserId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received location membership delete request actorUserId={} locationId={} targetUserId={}",
            authenticatedUserId,
            locationId,
            targetUserId
        );
        try {
            locationService.deleteLocationMembership(authenticatedUserId, locationId, targetUserId);
            log.info(
                "Completed location membership delete request actorUserId={} locationId={} targetUserId={}",
                authenticatedUserId,
                locationId,
                targetUserId
            );
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location membership delete request actorUserId={} locationId={} targetUserId={} status={} reason={}",
                authenticatedUserId,
                locationId,
                targetUserId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location membership delete request actorUserId={} locationId={} targetUserId={}",
                authenticatedUserId,
                locationId,
                targetUserId,
                ex
            );
            throw ex;
        }
    }
}
