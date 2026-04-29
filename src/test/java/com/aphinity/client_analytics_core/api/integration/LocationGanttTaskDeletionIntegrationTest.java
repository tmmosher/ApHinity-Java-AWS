package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskDependencyRepository;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import com.aphinity.client_analytics_core.api.core.services.gantt.LocationGanttTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationGanttTaskDeletionIntegrationTest extends AbstractApiIntegrationTest {
    @Autowired
    private LocationGanttTaskService locationGanttTaskService;

    @Autowired
    private GanttTaskRepository ganttTaskRepository;

    @Autowired
    private GanttTaskDependencyRepository ganttTaskDependencyRepository;

    @Test
    void deleteLocationTaskRemovesAllDependenciesInvolvingTheTask() {
        AppUser user = createUser("gantt.partner@example.com", "password", true, "partner");
        Location location = createLocation("Gantt delete location");

        GanttTask deletedTask = ganttTask(location, "Delete me");
        GanttTask prerequisiteTask = ganttTask(location, "Prerequisite");
        GanttTask dependentTask = ganttTask(location, "Dependent");
        ganttTaskRepository.saveAllAndFlush(List.of(deletedTask, prerequisiteTask, dependentTask));

        GanttTaskDependency outgoing = dependency(location, deletedTask, prerequisiteTask);
        GanttTaskDependency incoming = dependency(location, dependentTask, deletedTask);
        ganttTaskDependencyRepository.saveAllAndFlush(List.of(outgoing, incoming));

        locationGanttTaskService.deleteLocationTask(
            user.getId(),
            location.getId(),
            deletedTask.getId(),
            "203.0.113.8"
        );

        assertTrue(ganttTaskRepository.findById(deletedTask.getId()).isEmpty());
        assertTrue(ganttTaskRepository.findById(prerequisiteTask.getId()).isPresent());
        assertTrue(ganttTaskRepository.findById(dependentTask.getId()).isPresent());
        assertTrue(
            ganttTaskDependencyRepository
                .findByLocation_IdAndGanttTask_IdOrderByDependencyTask_IdAsc(location.getId(), deletedTask.getId())
                .isEmpty()
        );
        assertTrue(
            ganttTaskDependencyRepository
                .findByLocationIdAndDependencyTaskIdOrderByGanttTaskIdAsc(location.getId(), deletedTask.getId())
                .isEmpty()
        );
        assertEquals(
            List.of(),
            ganttTaskDependencyRepository
                .findByLocation_IdAndGanttTask_IdOrderByDependencyTask_IdAsc(location.getId(), dependentTask.getId())
                .stream()
                .map(GanttTaskDependency::getDependencyTaskId)
                .toList()
        );
    }

    private GanttTask ganttTask(Location location, String title) {
        GanttTask task = new GanttTask();
        task.setLocation(location);
        task.setTitle(title);
        task.setStartDate(LocalDate.parse("2026-04-01"));
        task.setEndDate(LocalDate.parse("2026-04-02"));
        return task;
    }

    private GanttTaskDependency dependency(Location location, GanttTask task, GanttTask dependencyTask) {
        GanttTaskDependency dependency = new GanttTaskDependency();
        dependency.setLocation(location);
        dependency.setGanttTask(task);
        dependency.setDependencyTask(dependencyTask);
        return dependency;
    }
}
