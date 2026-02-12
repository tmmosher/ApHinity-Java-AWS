package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.LocationInviteRequest;
import com.aphinity.client_analytics_core.api.core.response.LocationInviteResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationInviteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for location invitation lifecycle management.
 */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationInviteController {
    private final LocationInviteService locationInviteService;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationInviteController(
        LocationInviteService locationInviteService,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.locationInviteService = locationInviteService;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Creates a new invite for a location.
     *
     * @param jwt authenticated principal JWT
     * @param request validated invitation request
     * @return created invite
     */
    @PostMapping("/location-invites")
    public LocationInviteResponse invite(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody LocationInviteRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationInviteService.createInvite(userId, request.locationId(), request.invitedEmail());
    }

    /**
     * Lists pending, non-expired invites for the authenticated account email.
     *
     * @param jwt authenticated principal JWT
     * @return active invites
     */
    @GetMapping("/location-invites/active")
    public List<LocationInviteResponse> activeInvites(@AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationInviteService.getActiveInvites(userId);
    }

    /**
     * Accepts a pending invite.
     *
     * @param jwt authenticated principal JWT
     * @param inviteId invite identifier
     * @return accepted invite payload
     */
    @PostMapping("/location-invites/{inviteId}/accept")
    public LocationInviteResponse acceptInvite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long inviteId) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationInviteService.acceptInvite(userId, inviteId);
    }

    /**
     * Declines a pending invite.
     *
     * @param jwt authenticated principal JWT
     * @param inviteId invite identifier
     * @return updated invite payload
     */
    @PostMapping("/location-invites/{inviteId}/decline")
    public LocationInviteResponse declineInvite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long inviteId) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationInviteService.declineInvite(userId, inviteId);
    }
}
