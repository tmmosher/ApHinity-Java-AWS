package com.aphinity.client_analytics_core.api.core.response.gantt;

import java.time.Instant;
import java.time.LocalDate;

public record GanttTaskResponse(
    Long id,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
}
