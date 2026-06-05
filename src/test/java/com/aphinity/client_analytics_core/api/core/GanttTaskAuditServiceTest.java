package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GanttTaskAuditServiceTest {
    @Test
    void recordDeletedWritesGanttTaskAuditLineToLogger(CapturedOutput output) {
        GanttTaskAuditService auditService = new GanttTaskAuditService();
        GanttTask task = ganttTask();

        auditService.recordDeleted(5L, "203.0.113.8", task, List.of(11L, 3L, 11L));

        String content = output.getOut() + output.getErr();
        assertTrue(content.contains("gantt-task-audit"));
        assertTrue(content.contains("action=DELETED"));
        assertTrue(content.contains("actorUserId=5"));
        assertTrue(content.contains("actorIpAddress=\"203.0.113.8\""));
        assertTrue(content.contains("taskId=44"));
        assertTrue(content.contains("locationId=99"));
        assertTrue(content.contains("title=\"OPS\""));
        assertTrue(content.contains("description=\"Ops update\""));
        assertTrue(content.contains("dependencyTaskIds=[3, 11]"));
    }

    private GanttTask ganttTask() {
        Location location = new Location();
        location.setId(99L);

        GanttTask task = new GanttTask();
        task.setId(44L);
        task.setLocation(location);
        task.setTitle("OPS");
        task.setStartDate(LocalDate.parse("2026-04-01"));
        task.setEndDate(LocalDate.parse("2026-04-10"));
        task.setDescription("Ops update");
        task.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        task.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        return task;
    }

}
