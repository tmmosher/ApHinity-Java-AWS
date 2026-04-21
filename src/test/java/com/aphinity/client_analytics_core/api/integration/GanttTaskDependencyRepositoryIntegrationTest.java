package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskDependencyRepository;
import com.aphinity.client_analytics_core.api.core.repositories.gantt.GanttTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GanttTaskDependencyRepositoryIntegrationTest extends AbstractApiIntegrationTest {
    @Autowired
    private GanttTaskRepository ganttTaskRepository;

    @Autowired
    private GanttTaskDependencyRepository ganttTaskDependencyRepository;

    @Test
    void persistsAndLoadsTaskDependenciesUsingTheCompositeLocationForeignKey() {
        Location location = createLocation("Dependencies Location");
        GanttTask task = ganttTask(location, "Primary task");
        GanttTask dependency = ganttTask(location, "Prerequisite task");
        ganttTaskRepository.saveAllAndFlush(List.of(task, dependency));

        GanttTaskDependency link = new GanttTaskDependency();
        link.setLocation(location);
        link.setGanttTask(task);
        link.setDependencyTask(dependency);
        ganttTaskDependencyRepository.saveAndFlush(link);

        List<GanttTaskDependency> loadedDependencies = ganttTaskDependencyRepository
            .findByLocation_IdAndGanttTask_IdOrderByDependencyTask_IdAsc(location.getId(), task.getId());

        assertEquals(1, loadedDependencies.size());
        GanttTaskDependency loaded = loadedDependencies.getFirst();
        assertEquals(location.getId(), loaded.getLocationId());
        assertEquals(task.getId(), loaded.getGanttTaskId());
        assertEquals(dependency.getId(), loaded.getDependencyTaskId());
    }

    private GanttTask ganttTask(Location location, String title) {
        GanttTask task = new GanttTask();
        task.setLocation(location);
        task.setTitle(title);
        task.setStartDate(LocalDate.parse("2026-04-01"));
        task.setEndDate(LocalDate.parse("2026-04-02"));
        return task;
    }
}
