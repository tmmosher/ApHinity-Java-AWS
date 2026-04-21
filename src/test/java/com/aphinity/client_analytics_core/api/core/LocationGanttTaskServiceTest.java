package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttChartTemplateService;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskAuditService;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskAuthorizationService;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskRequestMapper;
import com.aphinity.client_analytics_core.api.core.services.gantt.LocationGanttTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LocationGanttTaskServiceTest {
    @Mock
    private LocationRepository locationRepository;

    @Mock
    private GanttTaskRepository ganttTaskRepository;

    @Mock
    private GanttTaskAuthorizationService authorizationService;

    @Mock
    private GanttTaskAuditService auditService;

    @Spy
    private GanttTaskRequestMapper requestMapper = new GanttTaskRequestMapper();

    @Spy
    private GanttChartTemplateService templateService = new GanttChartTemplateService();

    @InjectMocks
    private LocationGanttTaskService locationGanttTaskService;

    @Test
    void getAccessibleLocationTasksReturnsMappedResponsesForAuthorizedClient() {
        AppUser user = verifiedUser(5L);
        GanttTask first = ganttTask(31L, "OPS");
        GanttTask second = ganttTask(32L, "QMS");

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);
        when(ganttTaskRepository.findVisibleByLocationIdAndTitleSearch(99L, "ops")).thenReturn(List.of(first, second));

        List<GanttTaskResponse> response = locationGanttTaskService.getAccessibleLocationTasks(5L, 99L, "  ops ");

        assertEquals(List.of("OPS", "QMS"), response.stream().map(GanttTaskResponse::title).toList());
        verify(authorizationService).requireUser(5L);
        verify(authorizationService).requireReadableLocationAccess(user, 99L);
        verify(ganttTaskRepository).findVisibleByLocationIdAndTitleSearch(99L, "ops");
    }

    @Test
    void getGanttChartTemplateReturnsClassPathResourceForAuthorizedClient() throws Exception {
        AppUser user = verifiedUser(5L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);

        Resource resource = locationGanttTaskService.getGanttChartTemplate(5L, 99L);

        assertEquals("gantt_chart_template.xlsx", resource.getFilename());
        assertTrue(resource.exists());
        assertTrue(resource.contentLength() > 0);
    }

    @Test
    void getAccessibleLocationTasksUsesUnfilteredQueryWhenSearchTermIsNull() {
        AppUser user = verifiedUser(5L);
        GanttTask first = ganttTask(31L, "OPS");

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);
        when(ganttTaskRepository.findByLocation_IdOrderByStartDateAscEndDateAscIdAsc(99L)).thenReturn(List.of(first));

        List<GanttTaskResponse> response = locationGanttTaskService.getAccessibleLocationTasks(5L, 99L, null);

        assertEquals(List.of("OPS"), response.stream().map(GanttTaskResponse::title).toList());
        verify(authorizationService).requireUser(5L);
        verify(authorizationService).requireReadableLocationAccess(user, 99L);
        verify(ganttTaskRepository).findByLocation_IdOrderByStartDateAscEndDateAscIdAsc(99L);
        verify(ganttTaskRepository, never()).findVisibleByLocationIdAndTitleSearch(any(), any());
    }

    @Test
    void createLocationTaskPersistsNormalizedFieldsAndTouchesLocation() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(authorizationService.requireLocation(99L)).thenReturn(location);
        when(ganttTaskRepository.saveAndFlush(any(GanttTask.class))).thenAnswer(invocation -> {
            GanttTask task = invocation.getArgument(0);
            task.setId(44L);
            task.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            task.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return task;
        });

        GanttTaskResponse response = locationGanttTaskService.createLocationTask(
            5L,
            99L,
            request("  OPS  ", "  Operational update  ")
        );

        assertEquals(44L, response.id());
        assertEquals("OPS", response.title());
        assertEquals("Operational update", response.description());
        verify(ganttTaskRepository).saveAndFlush(any(GanttTask.class));
        verify(auditService).recordCreated(eq(5L), any(GanttTask.class));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void createLocationTaskRejectsTitlesShorterThanThreeCharacters() {
        AppUser user = verifiedUser(5L);
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(authorizationService.requireLocation(99L)).thenReturn(new Location());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationGanttTaskService.createLocationTask(5L, 99L, request("AB", "desc"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Task title must be between 3 and 60 characters", ex.getReason());
        verifyNoInteractions(ganttTaskRepository);
    }

    @Test
    void createLocationTasksBulkPersistsAllTasksAndTouchesLocationOnce() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(authorizationService.requireLocation(99L)).thenReturn(location);
        when(ganttTaskRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<GanttTask> tasks = invocation.getArgument(0, List.class);
            List<GanttTask> persisted = new ArrayList<>(tasks);
            for (int index = 0; index < persisted.size(); index += 1) {
                GanttTask task = persisted.get(index);
                task.setId(100L + index);
                task.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
                task.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            }
            return persisted;
        });

        List<GanttTaskResponse> response = locationGanttTaskService.createLocationTasksBulk(
            5L,
            99L,
            List.of(
                request("OPS", "Ops update"),
                request("QMS", "Quality update")
            )
        );

        assertEquals(2, response.size());
        assertEquals(List.of("OPS", "QMS"), response.stream().map(GanttTaskResponse::title).toList());
        verify(ganttTaskRepository).saveAllAndFlush(any());
        verify(auditService, times(2)).recordCreated(eq(5L), any(GanttTask.class));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void createLocationTasksBulkRejectsEmptyPayload() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(authorizationService.requireLocation(99L)).thenReturn(location);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationGanttTaskService.createLocationTasksBulk(5L, 99L, List.of())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("At least one gantt task is required", ex.getReason());
        verify(ganttTaskRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void updateLocationTaskPersistsUpdatedFieldsAndTouchesLocation() {
        AppUser user = verifiedUser(5L);
        GanttTask task = ganttTask(44L, "OPS");

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(ganttTaskRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(task));
        when(ganttTaskRepository.saveAndFlush(task)).thenAnswer(invocation -> invocation.getArgument(0));

        GanttTaskResponse response = locationGanttTaskService.updateLocationTask(
            5L,
            99L,
            44L,
            request("QMS", "Updated")
        );

        assertEquals("QMS", response.title());
        assertEquals("Updated", response.description());
        verify(ganttTaskRepository).saveAndFlush(task);
        verify(auditService).recordUpdated(eq(5L), any(GanttTask.class));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationTaskRejectsMissingTask() {
        AppUser user = verifiedUser(5L);
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(ganttTaskRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationGanttTaskService.updateLocationTask(5L, 99L, 44L, request("OPS", "desc"))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location gantt task not found", ex.getReason());
        verify(ganttTaskRepository, never()).saveAndFlush(any(GanttTask.class));
    }

    @Test
    void updateLocationTaskRejectsInvalidDateRangeBeforePersistence() {
        AppUser user = verifiedUser(5L);
        GanttTask task = ganttTask(44L, "OPS");
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(ganttTaskRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(task));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationGanttTaskService.updateLocationTask(
                5L,
                99L,
                44L,
                new LocationGanttTaskRequest(
                    "OPS",
                    LocalDate.parse("2026-04-10"),
                    LocalDate.parse("2026-04-01"),
                    "desc"
                )
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Task end date must be on or after the start date", ex.getReason());
        verify(ganttTaskRepository, never()).saveAndFlush(any(GanttTask.class));
        assertTrue(task.getEndDate().isEqual(LocalDate.parse("2026-04-10")));
    }

    @Test
    void deleteLocationTaskDeletesExistingTaskAndWritesAuditLog() {
        AppUser user = verifiedUser(5L);
        GanttTask task = ganttTask(44L, "OPS");
        String actorIpAddress = "203.0.113.8";

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(ganttTaskRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(task));

        locationGanttTaskService.deleteLocationTask(5L, 99L, 44L, actorIpAddress);

        verify(auditService).recordDeleted(5L, actorIpAddress, task);
        verify(ganttTaskRepository).delete(task);
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void deleteLocationTaskRejectsMissingTask() {
        AppUser user = verifiedUser(5L);
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireWritePermission(user, 99L);
        when(ganttTaskRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationGanttTaskService.deleteLocationTask(5L, 99L, 44L, "203.0.113.8")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location gantt task not found", ex.getReason());
        verify(authorizationService).requireLocationExists(99L);
        verify(ganttTaskRepository, never()).delete(any(GanttTask.class));
    }

    private LocationGanttTaskRequest request(String title, String description) {
        return new LocationGanttTaskRequest(
            title,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-10"),
            description
        );
    }

    private GanttTask ganttTask(Long id, String title) {
        GanttTask task = new GanttTask();
        task.setId(id);
        task.setTitle(title);
        task.setStartDate(LocalDate.parse("2026-04-01"));
        task.setEndDate(LocalDate.parse("2026-04-10"));
        task.setDescription("Ops update");
        task.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        task.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        return task;
    }

    private AppUser verifiedUser(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("verified@example.com");
        user.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
