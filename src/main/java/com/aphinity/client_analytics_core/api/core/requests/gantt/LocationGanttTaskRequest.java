package com.aphinity.client_analytics_core.api.core.requests.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record LocationGanttTaskRequest(
    @NotBlank(message = "Task title is required")
    @Size(
        min = GanttTask.TITLE_MIN_LENGTH,
        max = GanttTask.TITLE_MAX_LENGTH,
        message = "Task title must be between 3 and 60 characters"
    )
    String title,

    @NotNull(message = "Task start date is required")
    LocalDate startDate,

    @NotNull(message = "Task end date is required")
    LocalDate endDate,

    String description,

    List<Long> dependencyTaskIds
) {
    public LocationGanttTaskRequest(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String description
    ) {
        this(title, startDate, endDate, description, List.of());
    }

    public LocationGanttTaskRequest {
        dependencyTaskIds = dependencyTaskIds == null ? List.of() : List.copyOf(dependencyTaskIds);
    }
}
