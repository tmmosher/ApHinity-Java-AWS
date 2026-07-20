package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.requests.location.LocationRequest;
import com.aphinity.client_analytics_core.api.core.requests.location.LocationWorkOrderEmailUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationDetailsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP boundary for location catalogue and settings operations. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationController {
    private final LocationDetailsService service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationController(LocationDetailsService service, AuthenticatedUserService authenticatedUserService) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/locations")
    public List<LocationResponse> locations(@AuthenticationPrincipal Jwt jwt) {
        return service.getAccessibleLocations(userId(jwt));
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse createLocation(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationRequest request) {
        return service.createLocation(userId(jwt), request.name());
    }

    @GetMapping("/locations/{locationId}")
    public LocationResponse location(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        return service.getAccessibleLocation(userId(jwt), locationId);
    }

    @PutMapping("/locations/{locationId}")
    public LocationResponse updateLocation(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @Valid @RequestBody LocationRequest request
    ) {
        return service.updateLocationName(userId(jwt), locationId, request.name());
    }

    @PutMapping("/locations/{locationId}/work-order-email")
    public LocationResponse updateLocationWorkOrderEmail(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody LocationWorkOrderEmailUpdateRequest request
    ) {
        return service.updateWorkOrderEmail(userId(jwt), locationId, request.workOrderEmail());
    }

    private Long userId(Jwt jwt) {
        return authenticatedUserService.resolveAuthenticatedUserId(jwt);
    }
}
