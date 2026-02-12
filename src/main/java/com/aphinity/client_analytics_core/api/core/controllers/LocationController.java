package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.LocationMembershipRoleUpdateRequest;
import com.aphinity.client_analytics_core.api.core.requests.LocationRequest;
import com.aphinity.client_analytics_core.api.core.response.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Location and membership endpoints for authenticated users.
 * <p>
 * The controller resolves the authenticated user id from JWT and delegates all authorization
 * and business logic to {@link LocationService}.
 */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationController {
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
     * Creates or updates a membership role for a user in a location.
     *
     * @param jwt authenticated principal JWT
     * @param locationId location identifier
     * @param userId target user identifier
     * @param request validated request containing desired role
     * @return updated membership payload
     */
    @PutMapping("/locations/{locationId}/memberships/{userId}")
    public LocationMembershipResponse updateMembership(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long userId,
        @Valid @RequestBody LocationMembershipRoleUpdateRequest request
    ) {
        Long authenticatedUserId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationService.upsertLocationMembershipRole(
            authenticatedUserId,
            locationId,
            userId,
            request.userRole()
        );
    }
}
