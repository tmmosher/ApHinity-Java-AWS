package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphCreateRequest;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateBatchRequest;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphNameUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.dashboard.*;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationGraphApplication;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP boundary for location graph and section-layout operations. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationGraphController {
    private final LocationGraphApplication service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationGraphController(LocationGraphApplication service, AuthenticatedUserService authenticatedUserService) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/locations/{locationId}/graphs")
    public List<GraphResponse> graphs(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId,
        @RequestParam(required = false) Integer monthRange
    ) {
        return service.getAccessibleLocationGraphs(userId(jwt), locationId, monthRange);
    }

    @GetMapping("/locations/{locationId}/graphs/{graphId}/table-page")
    public LocationDashboardTablePageResponse tablePage(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable Long graphId,
        @RequestParam(required = false) Integer monthRange,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size
    ) {
        return service.getAccessibleLocationGraphTablePage(userId(jwt), locationId, graphId, monthRange, page, size);
    }

    @PostMapping("/locations/{locationId}/graphs")
    @ResponseStatus(HttpStatus.CREATED)
    public GraphResponse create(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId,
        @Valid @RequestBody LocationGraphCreateRequest request
    ) {
        return service.createLocationGraph(
            userId(jwt), locationId, request.sectionId(), Boolean.TRUE.equals(request.createNewSection()), request.graphType()
        );
    }

    @PutMapping("/locations/{locationId}/graphs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId,
        @Valid @RequestBody LocationGraphDataUpdateBatchRequest request
    ) {
        service.updateLocationGraphs(
            userId(jwt),
            locationId,
            request.graphs().stream()
                .map(update -> new LocationGraphApplication.GraphUpdateCommand(
                    update.graphId(), update.description(), update.data(), update.layout(), update.config(),
                    update.style(), update.expectedUpdatedAt()
                ))
                .toList(),
            request.sectionLayout(),
            request.monthRange()
        );
    }

    @PutMapping("/locations/{locationId}/graphs/{graphId}/name")
    public GraphNameUpdateResponse rename(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable Long graphId,
        @RequestBody LocationGraphNameUpdateRequest request
    ) {
        return service.updateLocationGraphName(userId(jwt), locationId, graphId, request == null ? null : request.name());
    }

    @DeleteMapping("/locations/{locationId}/graphs/{graphId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable Long graphId) {
        service.deleteLocationGraph(userId(jwt), locationId, graphId);
    }

    @DeleteMapping("/locations/{locationId}/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @PathVariable Long sectionId
    ) {
        service.deleteLocationSection(userId(jwt), locationId, sectionId);
    }

    private Long userId(Jwt jwt) {
        return authenticatedUserService.resolveAuthenticatedUserId(jwt);
    }
}
