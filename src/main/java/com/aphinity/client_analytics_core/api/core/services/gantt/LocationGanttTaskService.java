package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class LocationGanttTaskService {
    private static final Logger log = LoggerFactory.getLogger(LocationGanttTaskService.class);

    private final LocationRepository locationRepository;
    private final GanttTaskRepository ganttTaskRepository;
    private final GanttTaskAuthorizationService authorizationService;
    private final GanttTaskRequestMapper requestMapper;
    private final GanttTaskDependencyService dependencyService;
    private final GanttChartTemplateService templateService;
    private final GanttTaskAuditService auditService;

    public LocationGanttTaskService(
        LocationRepository locationRepository,
        GanttTaskRepository ganttTaskRepository,
        GanttTaskAuthorizationService authorizationService,
        GanttTaskRequestMapper requestMapper,
        GanttTaskDependencyService dependencyService,
        GanttChartTemplateService templateService,
        GanttTaskAuditService auditService
    ) {
        this.locationRepository = locationRepository;
        this.ganttTaskRepository = ganttTaskRepository;
        this.authorizationService = authorizationService;
        this.requestMapper = requestMapper;
        this.dependencyService = dependencyService;
        this.templateService = templateService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GanttTaskResponse> getAccessibleLocationTasks(Long userId, Long locationId, String searchTerm) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);

        String normalizedSearchTerm = normalizeSearchTerm(searchTerm);
        List<GanttTask> tasks = normalizedSearchTerm == null
            ? ganttTaskRepository.findByLocation_IdOrderByStartDateAscEndDateAscIdAsc(locationId)
            : ganttTaskRepository.findVisibleByLocationIdAndTitleSearch(locationId, normalizedSearchTerm);

        if (tasks.isEmpty()) {
            return List.of();
        }

        var dependencyIdsByTaskId = dependencyService.findDependencyTaskIdsByTaskIds(
            locationId,
            tasks.stream().map(GanttTask::getId).toList()
        );

        return tasks.stream()
            .map(task -> requestMapper.toResponse(
                task,
                dependencyIdsByTaskId.getOrDefault(task.getId(), List.of())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public Resource getGanttChartTemplate(Long userId, Long locationId) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireReadableLocationAccess(user, locationId);
        return templateService.getTemplate();
    }

    @Transactional
    public GanttTaskResponse createLocationTask(Long userId, Long locationId, LocationGanttTaskRequest request) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireWritePermission(user, locationId);

        Location location = authorizationService.requireLocation(locationId);
        GanttTask task = requestMapper.createTask(location, request);
        try {
            GanttTask persisted = ganttTaskRepository.saveAndFlush(task);
            List<Long> dependencyTaskIds = dependencyService.replaceDependencies(persisted, request.dependencyTaskIds());
            auditService.recordCreated(userId, persisted, dependencyTaskIds);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return requestMapper.toResponse(persisted, dependencyTaskIds);
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
    public List<GanttTaskResponse> createLocationTasksBulk(
        Long userId,
        Long locationId,
        List<LocationGanttTaskRequest> requests
    ) {
        AppUser user = authorizationService.requireUser(userId);
        authorizationService.requireWritePermission(user, locationId);
        Location location = authorizationService.requireLocation(locationId);

        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one gantt task is required");
        }

        List<GanttTask> tasks = new ArrayList<>(requests.size());
        for (LocationGanttTaskRequest request : requests) {
            tasks.add(requestMapper.createTask(location, request));
        }

        try {
            List<GanttTask> persisted = ganttTaskRepository.saveAllAndFlush(tasks);
            List<GanttTaskResponse> response = new ArrayList<>(persisted.size());
            for (int taskIndex = 0; taskIndex < persisted.size(); taskIndex += 1) {
                GanttTask task = persisted.get(taskIndex);
                List<Long> dependencyTaskIds = dependencyService.replaceDependencies(
                    task,
                    requests.get(taskIndex).dependencyTaskIds()
                );
                auditService.recordCreated(userId, task, dependencyTaskIds);
                response.add(requestMapper.toResponse(task, dependencyTaskIds));
            }
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return response;
        } catch (RuntimeException ex) {
            log.error(
                "Gantt task bulk creation persistence failed actorUserId={} locationId={} taskCount={}",
                userId,
                locationId,
                requests.size(),
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
            List<Long> dependencyTaskIds = dependencyService.replaceDependencies(task, request.dependencyTaskIds());
            task.setUpdatedAt(Instant.now());
            GanttTask persisted = ganttTaskRepository.saveAndFlush(task);
            auditService.recordUpdated(userId, persisted, dependencyTaskIds);
            locationRepository.touchUpdatedAt(locationId, Instant.now());
            return requestMapper.toResponse(persisted, dependencyTaskIds);
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
                List<Long> dependencyTaskIds = dependencyService.findDependencyTaskIds(locationId, taskId);
                auditService.recordDeleted(userId, actorIpAddress, task, dependencyTaskIds);
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
