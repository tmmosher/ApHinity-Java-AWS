package com.aphinity.client_analytics_core.api.core.entities.servicecalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * File-backed audit payload for service calendar changes.
 * The async logger writes this payload as a single log line.
 */
public record ServiceEventAuditLog(
    Long serviceEventId,
    Long locationId,
    Long actorUserId,
    String actorIpAddress,
    ServiceEventAuditAction action,
    String title,
    ServiceEventResponsibility responsibility,
    LocalDate eventDate,
    LocalTime eventTime,
    LocalDate endEventDate,
    LocalTime endEventTime,
    String description,
    ServiceEventStatus status,
    Instant serviceEventCreatedAt,
    Instant serviceEventUpdatedAt,
    Instant recordedAt
) {
    public static ServiceEventAuditLog from(
        Long actorUserId,
        String actorIpAddress,
        ServiceEvent serviceEvent,
        ServiceEventAuditAction action
    ) {
        Objects.requireNonNull(serviceEvent, "serviceEvent");
        Objects.requireNonNull(action, "action");
        return new ServiceEventAuditLog(
            serviceEvent.getId(),
            serviceEvent.getLocation().getId(),
            actorUserId,
            normalize(actorIpAddress),
            action,
            normalize(serviceEvent.getTitle()),
            serviceEvent.getResponsibility(),
            serviceEvent.getEventDate(),
            serviceEvent.getEventTime(),
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime(),
            normalizeNullable(serviceEvent.getDescription()),
            serviceEvent.getStatus(),
            serviceEvent.getCreatedAt(),
            serviceEvent.getUpdatedAt(),
            Instant.now()
        );
    }

    public String toLogLine() {
        return "service-event-audit"
            + " action=" + action
            + " actorUserId=" + valueOrNull(actorUserId)
            + " actorIpAddress=" + quoteOrNull(actorIpAddress)
            + " eventId=" + valueOrNull(serviceEventId)
            + " locationId=" + valueOrNull(locationId)
            + " title=" + quoteOrNull(title)
            + " responsibility=" + valueOrNull(responsibility)
            + " eventDate=" + valueOrNull(eventDate)
            + " eventTime=" + valueOrNull(eventTime)
            + " endEventDate=" + valueOrNull(endEventDate)
            + " endEventTime=" + valueOrNull(endEventTime)
            + " status=" + valueOrNull(status)
            + " description=" + quoteOrNull(description)
            + " serviceEventCreatedAt=" + valueOrNull(serviceEventCreatedAt)
            + " serviceEventUpdatedAt=" + valueOrNull(serviceEventUpdatedAt)
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
