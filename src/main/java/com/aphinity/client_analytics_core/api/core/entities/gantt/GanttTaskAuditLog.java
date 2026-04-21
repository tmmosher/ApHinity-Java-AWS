package com.aphinity.client_analytics_core.api.core.entities.gantt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * File-backed audit payload for gantt task changes.
 * The async logger writes this payload as a single log line.
 */
public record GanttTaskAuditLog(
    Long ganttTaskId,
    Long locationId,
    Long actorUserId,
    String actorIpAddress,
    GanttTaskAuditAction action,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    List<Long> dependencyTaskIds,
    Instant taskCreatedAt,
    Instant taskUpdatedAt,
    Instant recordedAt
) {
    public static GanttTaskAuditLog from(
        Long actorUserId,
        String actorIpAddress,
        GanttTask task,
        List<Long> dependencyTaskIds,
        GanttTaskAuditAction action
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(action, "action");
        return new GanttTaskAuditLog(
            task.getId(),
            task.getLocation().getId(),
            actorUserId,
            normalize(actorIpAddress),
            action,
            normalize(task.getTitle()),
            task.getStartDate(),
            task.getEndDate(),
            normalizeNullable(task.getDescription()),
            normalizeDependencyTaskIds(dependencyTaskIds),
            task.getCreatedAt(),
            task.getUpdatedAt(),
            Instant.now()
        );
    }

    public String toLogLine() {
        return "gantt-task-audit"
            + " action=" + action
            + " actorUserId=" + valueOrNull(actorUserId)
            + " actorIpAddress=" + quoteOrNull(actorIpAddress)
            + " taskId=" + valueOrNull(ganttTaskId)
            + " locationId=" + valueOrNull(locationId)
            + " title=" + quoteOrNull(title)
            + " startDate=" + valueOrNull(startDate)
            + " endDate=" + valueOrNull(endDate)
            + " description=" + quoteOrNull(description)
            + " dependencyTaskIds=" + valueOrNull(dependencyTaskIds)
            + " taskCreatedAt=" + valueOrNull(taskCreatedAt)
            + " taskUpdatedAt=" + valueOrNull(taskUpdatedAt)
            + " recordedAt=" + valueOrNull(recordedAt);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeNullable(String value) {
        return normalize(value);
    }

    private static List<Long> normalizeDependencyTaskIds(List<Long> dependencyTaskIds) {
        if (dependencyTaskIds == null || dependencyTaskIds.isEmpty()) {
            return List.of();
        }
        return dependencyTaskIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .sorted()
            .toList();
    }

    private static String valueOrNull(Object value) {
        return value == null ? "null" : value.toString();
    }

    private static String quoteOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}
