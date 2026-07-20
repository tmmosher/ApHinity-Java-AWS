package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationAlertSubscriptionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** HTTP boundary for personal location-alert subscriptions. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationAlertSubscriptionController {
    private final LocationAlertSubscriptionService service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationAlertSubscriptionController(
        LocationAlertSubscriptionService service, AuthenticatedUserService authenticatedUserService
    ) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PutMapping("/locations/{locationId}/alerts/subscription")
    public LocationResponse subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        return service.subscribe(userId(jwt), locationId);
    }

    @DeleteMapping("/locations/{locationId}/alerts/subscription")
    public LocationResponse unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        return service.unsubscribe(userId(jwt), locationId);
    }

    private Long userId(Jwt jwt) { return authenticatedUserService.resolveAuthenticatedUserId(jwt); }
}
