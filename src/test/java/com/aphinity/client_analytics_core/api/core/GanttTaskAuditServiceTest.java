package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.services.gantt.GanttTaskAuditService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import com.aphinity.client_analytics_core.logging.AppLoggingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GanttTaskAuditServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void recordDeletedWritesGanttTaskAuditLineToAsyncLoggerFile() throws Exception {
        AsyncLogService asyncLogService = new AsyncLogService(loggingProperties(tempDir, 4));
        try {
            GanttTaskAuditService auditService = new GanttTaskAuditService(asyncLogService);
            GanttTask task = ganttTask();

            auditService.recordDeleted(5L, "203.0.113.8", task, List.of(11L, 3L, 11L));
            stopAsyncLogService(asyncLogService);

            try (var files = Files.list(tempDir)) {
                List<Path> fileList = files.toList();
                assertEquals(1, fileList.size());

                String content = Files.readString(fileList.getFirst());
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
        } finally {
            stopAsyncLogService(asyncLogService);
        }
    }

    private AppLoggingProperties loggingProperties(Path directory, int queueCapacity) {
        AppLoggingProperties properties = new AppLoggingProperties();
        properties.setDirectory(directory.toString());
        properties.setFilePrefix("gantt-audit-test");
        properties.setQueueCapacity(queueCapacity);
        properties.setFlushIntervalMs(1000);
        return properties;
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

    private void stopAsyncLogService(AsyncLogService asyncLogService) throws Exception {
        Method stopMethod = AsyncLogService.class.getDeclaredMethod("stop");
        stopMethod.setAccessible(true);
        stopMethod.invoke(asyncLogService);
    }
}
