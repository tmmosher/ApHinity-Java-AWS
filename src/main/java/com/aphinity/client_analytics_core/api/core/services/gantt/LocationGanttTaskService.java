package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class LocationGanttTaskService {
    private static final Logger log = LoggerFactory.getLogger(LocationGanttTaskService.class);

    private final LocationRepository locationRepository;
    private final GanttTaskRepository ganttTaskRepository;
    private final GanttTaskAuthorizationService authorizationService;
    private final GanttTaskRequestMapper requestMapper;
    private final GanttTaskAuditService auditService;

    public LocationGanttTaskService(
        LocationRepository locationRepository,
        GanttTaskRepository ganttTaskRepository,
        GanttTaskAuthorizationService authorizationService,
        GanttTaskRequestMapper requestMapper,
        GanttTaskAuditService auditService
    ) {
        this.locationRepository = locationRepository;
        this.ganttTaskRepository = ganttTaskRepository;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GanttTaskResponse> getAccessibleLocationTasks(Long userId, Long locationId, String searchTerm) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);

        return ganttTaskRepository.findVisibleByLocationIdAndTitleSearch(locationId, normalizeSearchTerm(searchTerm)).stream()
            .map(requestMapper::toResponse)
            .toList();
    }

    @Transactional
    public GanttTaskResponse createLocationTask(Long userId, Long locationId, LocationGanttTaskRequest request) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireWritePermission(user, locationId);

        Location location = authorizationService.requireLocation(locationId);
        GanttTask task = requestMapper.createTask(location, request);
        try {
            GanttTask persisted = ganttTaskRepository.saveAndFlush(task);
            auditService.recordCreated(userId, persisted);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return requestMapper.toResponse(persisted);
        } catch (RuntimeException ex) {
            log.error(
                "Gantt task creation persistence failed actorUserId={} locationId={}",
                userId,
                locationId,
                ex
            );
            throw ex;
        }
    }

    @Transactional
    public GanttTaskResponse updateLocationTask(
        Long userId,
        Long locationId,
        Long taskId,
        LocationGanttTaskRequest request
    ) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireWritePermission(user, locationId);
        authorizationService.requireLocationExists(locationId);

        GanttTask task = ganttTaskRepository.findByIdAndLocation_Id(taskId, locationId)
            .orElseThrow(this::ganttTaskNotFound);
        requestMapper.applyRequest(task, request);

        try {
            GanttTask persisted = ganttTaskRepository.saveAndFlush(task);
            auditService.recordUpdated(userId, persisted);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return requestMapper.toResponse(persisted);
        } catch (RuntimeException ex) {
            log.error(
                "Gantt task update persistence failed actorUserId={} locationId={} taskId={}",
                userId,
                locationId,
                taskId,
                ex
            );
            throw ex;
        }
    }

    @Transactional
    public void deleteLocationTask(Long userId, Long locationId, Long taskId, String actorIpAddress) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireWritePermission(user, locationId);

        GanttTask task = ganttTaskRepository.findByIdAndLocation_Id(taskId, locationId).orElse(null);
        if (task != null) {
            try {
                auditService.recordDeleted(userId, actorIpAddress, task);
                ganttTaskRepository.delete(task);
                locationRepository.touchUpdatedAt(locationId, Instant.now());
            } catch (RuntimeException ex) {
                log.error(
                    "Gantt task delete persistence failed actorUserId={} locationId={} taskId={}",
                    userId,
                    locationId,
                    taskId,
                    ex
                );
                throw ex;
            }
            return;
        }

        authorizationService.requireLocationExists(locationId);
        throw ganttTaskNotFound();
    }

    private String normalizeSearchTerm(String searchTerm) {
        if (searchTerm == null) {
            return null;
        }
        String normalized = searchTerm.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private ResponseStatusException ganttTaskNotFound() {
        return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Location gantt task not found");
    }
}
