package com.aphinity.client_analytics_core.api.core.controllers.servicecalendar;

import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceCalendarUploadResponse;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.LocationEventService;
import com.aphinity.client_analytics_core.api.security.ClientRequestMetadataResolver;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationEventController {
    private static final Logger log = LoggerFactory.getLogger(LocationEventController.class);
    private static final MediaType EXCEL_MEDIA_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LocationEventService locationEventService;
    private final AuthenticatedUserService authenticatedUserService;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    public LocationEventController(
        LocationEventService locationEventService,
        AuthenticatedUserService authenticatedUserService,
        ClientRequestMetadataResolver requestMetadataResolver
    ) {
        this.locationEventService = locationEventService;
        this.authenticatedUserService = authenticatedUserService;
        this.requestMetadataResolver = requestMetadataResolver;
    }

    @GetMapping("/locations/{locationId}/events")
    public List<ServiceEventResponse> locationEvents(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @RequestParam(required = false) String month
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationEventService.getAccessibleLocationEvents(userId, locationId, resolveViewedMonth(month));
    }

    @GetMapping("/locations/{locationId}/events/template")
    public ResponseEntity<Resource> downloadLocationEventTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        Resource templateResource = locationEventService.getServiceCalendarTemplate(userId, locationId);

        try {
            return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .contentLength(templateResource.contentLength())
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                        .filename(templateResource.getFilename())
                        .build()
                        .toString()
                )
                .body(templateResource);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Service calendar template unavailable", ex);
        }
    }

    @PostMapping(path = "/locations/{locationId}/events/calendar-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCalendarUploadResponse uploadLocationEventCalendar(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @RequestParam("file") MultipartFile file
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received service calendar upload request actorUserId={} locationId={} filename={}",
            userId,
            locationId,
            file == null ? null : file.getOriginalFilename()
        );
        try {
            int importedCount = locationEventService.uploadServiceCalendar(userId, locationId, file);
            log.info(
                "Completed service calendar upload request actorUserId={} locationId={} importedCount={}",
                userId,
                locationId,
                importedCount
            );
            return new ServiceCalendarUploadResponse(importedCount);
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected service calendar upload request actorUserId={} locationId={} status={} reason={}",
                userId,
                locationId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (ApiClientException ex) {
            log.warn(
                "Rejected service calendar upload request actorUserId={} locationId={} status={} code={} message={}",
                userId,
                locationId,
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed service calendar upload request actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }
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
        @PathVariable Long eventId,
        HttpServletRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        String clientIpAddress = requestMetadataResolver.resolveClientIp(request);
        log.info(
            "Received location event delete request actorUserId={} actorIpAddress={} locationId={} eventId={}",
            userId,
            clientIpAddress,
            locationId,
            eventId
        );
        try {
            locationEventService.deleteLocationEvent(userId, locationId, eventId, clientIpAddress);
            log.info(
                "Completed location event delete request actorUserId={} actorIpAddress={} locationId={} eventId={}",
                userId,
                clientIpAddress,
                locationId,
                eventId
            );
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected location event delete request actorUserId={} actorIpAddress={} locationId={} eventId={} status={} reason={}",
                userId,
                clientIpAddress,
                locationId,
                eventId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed location event delete request actorUserId={} actorIpAddress={} locationId={} eventId={}",
                userId,
                clientIpAddress,
                locationId,
                eventId,
                ex
            );
            throw ex;
        }
    }

    private YearMonth resolveViewedMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must use yyyy-MM format");
        }
    }
}
