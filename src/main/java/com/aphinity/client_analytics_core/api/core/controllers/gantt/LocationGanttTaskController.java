package com.aphinity.client_analytics_core.api.core.controllers.gantt;

import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.gantt.LocationGanttTaskService;
import com.aphinity.client_analytics_core.api.security.ClientRequestMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationGanttTaskController {
    private static final Logger log = LoggerFactory.getLogger(LocationGanttTaskController.class);
    private static final MediaType EXCEL_MEDIA_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LocationGanttTaskService locationGanttTaskService;
    private final AuthenticatedUserService authenticatedUserService;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    public LocationGanttTaskController(
        LocationGanttTaskService locationGanttTaskService,
        AuthenticatedUserService authenticatedUserService,
        ClientRequestMetadataResolver requestMetadataResolver
    ) {
        this.locationGanttTaskService = locationGanttTaskService;
        this.authenticatedUserService = authenticatedUserService;
        this.requestMetadataResolver = requestMetadataResolver;
    }

    @GetMapping("/locations/{locationId}/gantt-tasks")
    public List<GanttTaskResponse> ganttTasks(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @RequestParam(required = false) String search
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        return locationGanttTaskService.getAccessibleLocationTasks(userId, locationId, search);
    }

    @GetMapping("/locations/{locationId}/gantt-tasks/template")
    public ResponseEntity<Resource> downloadLocationGanttTaskTemplate(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        Resource templateResource = locationGanttTaskService.getGanttChartTemplate(userId, locationId);

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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gantt chart template unavailable", ex);
        }
    }

    @PostMapping("/locations/{locationId}/gantt-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public GanttTaskResponse createGanttTask(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody LocationGanttTaskRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info("Received gantt task create request actorUserId={} locationId={}", userId, locationId);
        try {
            GanttTaskResponse response = locationGanttTaskService.createLocationTask(userId, locationId, request);
            log.info(
                "Completed gantt task create request actorUserId={} locationId={} taskId={}",
                userId,
                locationId,
                response.id()
            );
            return response;
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected gantt task create request actorUserId={} locationId={} status={} reason={}",
                userId,
                locationId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed gantt task create request actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }
    }

    @PostMapping("/locations/{locationId}/gantt-tasks/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GanttTaskResponse> createGanttTasksBulk(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @Valid @RequestBody List<@Valid LocationGanttTaskRequest> requests
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received gantt task bulk create request actorUserId={} locationId={} taskCount={}",
            userId,
            locationId,
            requests == null ? 0 : requests.size()
        );
        try {
            List<GanttTaskResponse> response = locationGanttTaskService.createLocationTasksBulk(userId, locationId, requests);
            log.info(
                "Completed gantt task bulk create request actorUserId={} locationId={} importedCount={}",
                userId,
                locationId,
                response.size()
            );
            return response;
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected gantt task bulk create request actorUserId={} locationId={} status={} reason={}",
                userId,
                locationId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed gantt task bulk create request actorUserId={} locationId={} taskCount={}",
                userId,
                locationId,
                requests == null ? 0 : requests.size(),
                ex
            );
            throw ex;
        }
    }

    @PutMapping("/locations/{locationId}/gantt-tasks/{taskId}")
    public GanttTaskResponse updateGanttTask(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long taskId,
        @Valid @RequestBody LocationGanttTaskRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        log.info(
            "Received gantt task update request actorUserId={} locationId={} taskId={}",
            userId,
            locationId,
            taskId
        );
        try {
            GanttTaskResponse response = locationGanttTaskService.updateLocationTask(userId, locationId, taskId, request);
            log.info(
                "Completed gantt task update request actorUserId={} locationId={} taskId={}",
                userId,
                locationId,
                taskId
            );
            return response;
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected gantt task update request actorUserId={} locationId={} taskId={} status={} reason={}",
                userId,
                locationId,
                taskId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed gantt task update request actorUserId={} locationId={} taskId={}",
                userId,
                locationId,
                taskId,
                ex
            );
            throw ex;
        }
    }

    @DeleteMapping("/locations/{locationId}/gantt-tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGanttTask(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long locationId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        Long userId = authenticatedUserService.resolveAuthenticatedUserId(jwt);
        String clientIpAddress = requestMetadataResolver.resolveClientIp(request);
        log.info(
            "Received gantt task delete request actorUserId={} actorIpAddress={} locationId={} taskId={}",
            userId,
            clientIpAddress,
            locationId,
            taskId
        );
        try {
            locationGanttTaskService.deleteLocationTask(userId, locationId, taskId, clientIpAddress);
            log.info(
                "Completed gantt task delete request actorUserId={} actorIpAddress={} locationId={} taskId={}",
                userId,
                clientIpAddress,
                locationId,
                taskId
            );
        } catch (ResponseStatusException ex) {
            log.warn(
                "Rejected gantt task delete request actorUserId={} actorIpAddress={} locationId={} taskId={} status={} reason={}",
                userId,
                clientIpAddress,
                locationId,
                taskId,
                ex.getStatusCode().value(),
                ex.getReason()
            );
            throw ex;
        } catch (RuntimeException ex) {
            log.error(
                "Failed gantt task delete request actorUserId={} actorIpAddress={} locationId={} taskId={}",
                userId,
                clientIpAddress,
                locationId,
                taskId,
                ex
            );
            throw ex;
        }
    }
}
