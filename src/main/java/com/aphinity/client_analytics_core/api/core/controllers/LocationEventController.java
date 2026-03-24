package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationEventService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationEventController {
    private static final Logger log = LoggerFactory.getLogger(LocationEventController.class);

    private final LocationEventService locationEventService;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationEventController(
        LocationEventService locationEventService,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.locationEventService = locationEventService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/locations/{locationId}/events")
    public List<ServiceEventResponse> locationEvents(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationEventService.getAccessibleLocationEvents(userId, locationId);
    }

    @PostMapping("/locations/{locationId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceEventResponse createLocationEvent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody LocationEventRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info("Received location event create request actorUserId={} locationId={}", userId, locationId);
        try {
            ServiceEventResponse response = locationEventService.createLocationEvent(userId, locationId, request);
            log.info(
                "Completed location event create request actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                response.id()
            );
            return response;
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location event create request actorUserId={} locationId={} status={} reason={}",
                userId,
                locationId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location event create request actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }
    }

    @PutMapping("/locations/{locationId}/events/{eventId}")
    public ServiceEventResponse updateLocationEvent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long eventId,
        @Valid @RequestBody LocationEventRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received location event update request actorUserId={} locationId={} eventId={}",
            userId,
            locationId,
            eventId
        );
        try {
            ServiceEventResponse response = locationEventService.updateLocationEvent(userId, locationId, eventId, request);
            log.info(
                "Completed location event update request actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                eventId
            );
            return response;
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location event update request actorUserId={} locationId={} eventId={} status={} reason={}",
                userId,
                locationId,
                eventId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location event update request actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                eventId,
                ex
            );
            throw ex;
        }
    }

    @DeleteMapping("/locations/{locationId}/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocationEvent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long eventId
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received location event delete request actorUserId={} locationId={} eventId={}",
            userId,
            locationId,
            eventId
        );
        try {
            locationEventService.deleteLocationEvent(userId, locationId, eventId);
            log.info(
                "Completed location event delete request actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                eventId
            );
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location event delete request actorUserId={} locationId={} eventId={} status={} reason={}",
                userId,
                locationId,
                eventId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location event delete request actorUserId={} locationId={} eventId={}",
                userId,
                locationId,
                eventId,
                ex
            );
            throw ex;
        }
    }
}
