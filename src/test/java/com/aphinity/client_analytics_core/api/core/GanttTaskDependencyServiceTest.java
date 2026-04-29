package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskDependencyRepository;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskDependencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GanttTaskDependencyServiceTest {
    @Mock
    private GanttTaskRepository ganttTaskRepository;

    @Mock
    private GanttTaskDependencyRepository ganttTaskDependencyRepository;

    @InjectMocks
    private GanttTaskDependencyService dependencyService;

    @Test
    void replaceDependenciesPersistsNormalizedDependencyTaskIdsAndDeletesOldLinks() {
        Location location = location(99L);
        GanttTask task = ganttTask(44L, location);
        GanttTask firstDependency = ganttTask(7L, location);
        GanttTask secondDependency = ganttTask(11L, location);

        when(ganttTaskDependencyRepository.findDependencyTaskIdsByLocationIdAndGanttTaskId(99L, 44L))
            .thenReturn(List.of(7L));
        when(ganttTaskRepository.findByLocation_IdAndIdInOrderByIdAsc(99L, List.of(7L, 11L)))
            .thenReturn(List.of(firstDependency, secondDependency));
        when(ganttTaskDependencyRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Long> dependencyTaskIds = dependencyService.replaceDependencies(task, List.of(11L, 7L, 11L, 0L, -3L));

        assertEquals(List.of(7L, 11L), dependencyTaskIds);
        verify(ganttTaskDependencyRepository).deleteByLocation_IdAndGanttTask_Id(99L, 44L);
        ArgumentCaptor<List<GanttTaskDependency>> savedDependenciesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ganttTaskDependencyRepository).saveAllAndFlush(savedDependenciesCaptor.capture());
        List<GanttTaskDependency> savedDependencies = savedDependenciesCaptor.getValue();
        assertEquals(2, savedDependencies.size());
        assertEquals(44L, savedDependencies.get(0).getGanttTask().getId());
        assertEquals(7L, savedDependencies.get(0).getDependencyTask().getId());
        assertEquals(11L, savedDependencies.get(1).getDependencyTask().getId());
    }

    @Test
    void replaceDependenciesReturnsEarlyWhenTheDependencySetIsUnchanged() {
        Location location = location(99L);
        GanttTask task = ganttTask(44L, location);
        GanttTask firstDependency = ganttTask(7L, location);
        GanttTask secondDependency = ganttTask(11L, location);

        when(ganttTaskDependencyRepository.findDependencyTaskIdsByLocationIdAndGanttTaskId(99L, 44L))
            .thenReturn(List.of(7L, 11L));

        List<Long> dependencyTaskIds = dependencyService.replaceDependencies(task, List.of(11L, 7L, 11L));

        assertEquals(List.of(7L, 11L), dependencyTaskIds);
        verify(ganttTaskDependencyRepository, never()).deleteByLocation_IdAndGanttTask_Id(any(), any());
        verify(ganttTaskDependencyRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(ganttTaskRepository);
    }

    @Test
    void replaceDependenciesRejectsSelfDependencyBeforeAnyPersistenceWork() {
        Location location = location(99L);
        GanttTask task = ganttTask(44L, location);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            dependencyService.replaceDependencies(task, List.of(44L))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Task cannot depend on itself", ex.getReason());
        verifyNoInteractions(ganttTaskRepository, ganttTaskDependencyRepository);
    }

    @Test
    void replaceDependenciesRejectsDependencyIdsThatDoNotResolveWithinTheLocation() {
        Location location = location(99L);
        GanttTask task = ganttTask(44L, location);
        GanttTask firstDependency = ganttTask(7L, location);

        when(ganttTaskDependencyRepository.findDependencyTaskIdsByLocationIdAndGanttTaskId(99L, 44L))
            .thenReturn(List.of());
        when(ganttTaskRepository.findByLocation_IdAndIdInOrderByIdAsc(99L, List.of(7L, 11L)))
            .thenReturn(List.of(firstDependency));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            dependencyService.replaceDependencies(task, List.of(11L, 7L))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Task dependencies must belong to this location", ex.getReason());
        verify(ganttTaskDependencyRepository, never()).deleteByLocation_IdAndGanttTask_Id(any(), any());
        verify(ganttTaskDependencyRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void deleteDependenciesForTaskDeletesOutgoingAndIncomingLinks() {
        dependencyService.deleteDependenciesForTask(99L, 44L);

        verify(ganttTaskDependencyRepository).deleteByLocation_IdAndGanttTask_Id(99L, 44L);
        verify(ganttTaskDependencyRepository).deleteByLocationIdAndDependencyTaskId(99L, 44L);
    }

    @Test
    void findDependencyTaskIdsByTaskIdsGroupsDependenciesByTaskId() {
        Location location = location(99L);
        GanttTask task44 = ganttTask(44L, location);
        GanttTask task45 = ganttTask(45L, location);
        GanttTask dependency7 = ganttTask(7L, location);
        GanttTask dependency11 = ganttTask(11L, location);

        when(ganttTaskDependencyRepository.findByLocation_IdAndGanttTask_IdInOrderByGanttTask_IdAscDependencyTask_IdAsc(
            99L,
            List.of(44L, 45L)
        )).thenReturn(List.of(
            dependencyLink(location, task44, dependency7),
            dependencyLink(location, task44, dependency11),
            dependencyLink(location, task45, dependency7)
        ));

        Map<Long, List<Long>> dependencyIdsByTaskId = dependencyService.findDependencyTaskIdsByTaskIds(
            99L,
            List.of(44L, 45L)
        );

        assertEquals(Map.of(
            44L,
            List.of(7L, 11L),
            45L,
            List.of(7L)
        ), dependencyIdsByTaskId);
    }

    private GanttTaskDependency dependencyLink(Location location, GanttTask task, GanttTask dependencyTask) {
        GanttTaskDependency dependency = new GanttTaskDependency();
        dependency.setLocation(location);
        dependency.setGanttTask(task);
        dependency.setDependencyTask(dependencyTask);
        return dependency;
    }

    private Location location(Long id) {
        Location location = new Location();
        location.setId(id);
        return location;
    }

    private GanttTask ganttTask(Long id, Location location) {
        GanttTask task = new GanttTask();
        task.setId(id);
        task.setLocation(location);
        task.setTitle("Task " + id);
        task.setStartDate(LocalDate.parse("2026-04-01"));
        task.setEndDate(LocalDate.parse("2026-04-10"));
        task.setDescription("Desc " + id);
        task.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        task.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        return task;
    }
}
