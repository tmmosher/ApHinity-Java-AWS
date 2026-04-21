package com.aphinity.client_analytics_core.api.core.response.gantt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GanttTaskResponse(
    Long id,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    Instant createdAt,
    Instant updatedAt,
    List<Long> dependencyTaskIds
) {
    public GanttTaskResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, title, startDate, endDate, description, createdAt, updatedAt, List.of());
    }

    public GanttTaskResponse {
        dependencyTaskIds = dependencyTaskIds == null ? List.of() : List.copyOf(dependencyTaskIds);
    }
}
