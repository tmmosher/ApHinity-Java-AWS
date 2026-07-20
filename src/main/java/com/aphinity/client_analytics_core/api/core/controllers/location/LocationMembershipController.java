package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.response.location.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP boundary for administrative location memberships. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationMembershipController {
    private final LocationMembershipService service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationMembershipController(LocationMembershipService service, AuthenticatedUserService authenticatedUserService) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/locations/{locationId}/memberships")
    public List<LocationMembershipResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        return service.getMemberships(userId(jwt), locationId);
    }

    @PutMapping("/locations/{locationId}/memberships/{userId}")
    public LocationMembershipResponse upsert(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable Long userId
    ) {
        return service.upsertMembership(userId(jwt), locationId, userId);
    }

    @DeleteMapping("/locations/{locationId}/memberships/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable("userId") Long targetUserId
    ) {
        service.deleteMembership(userId(jwt), locationId, targetUserId);
    }

    private Long userId(Jwt jwt) { return authenticatedUserService.resolveAuthenticatedUserId(jwt); }
}
