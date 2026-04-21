package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTaskDependency;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GanttTaskDependencyTest {
    @Test
    void associationSettersSynchronizeScalarForeignKeyIds() {
        Location location = location(99L);
        GanttTask ganttTask = ganttTask(44L, location);
        GanttTask dependencyTask = ganttTask(7L, location);

        GanttTaskDependency dependency = new GanttTaskDependency();
        dependency.setLocation(location);
        dependency.setGanttTask(ganttTask);
        dependency.setDependencyTask(dependencyTask);

        assertEquals(99L, dependency.getLocationId());
        assertEquals(44L, dependency.getGanttTaskId());
        assertEquals(7L, dependency.getDependencyTaskId());
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
        return task;
    }
}
