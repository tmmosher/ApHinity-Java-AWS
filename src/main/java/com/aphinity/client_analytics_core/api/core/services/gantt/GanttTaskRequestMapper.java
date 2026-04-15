package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.core.entities.gantt.GanttTask;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class GanttTaskRequestMapper {
    public GanttTask createTask(Location location, LocationGanttTaskRequest request) {
        GanttTask task = new GanttTask();
        task.setLocation(location);
        applyRequest(task, request);
        return task;
    }

    public void applyRequest(GanttTask task, LocationGanttTaskRequest request) {
        String title = normalizeTitle(request == null ? null : request.title());
        LocalDate startDate = normalizeStartDate(request == null ? null : request.startDate());
        LocalDate endDate = normalizeEndDate(request == null ? null : request.endDate());
        validateDateRange(startDate, endDate);

        task.setTitle(title);
        task.setStartDate(startDate);
        task.setEndDate(endDate);
        task.setDescription(normalizeDescription(request == null ? null : request.description()));
    }

    public GanttTaskResponse toResponse(GanttTask task) {
        return new GanttTaskResponse(
            task.getId(),
            task.getTitle(),
            task.getStartDate(),
            task.getEndDate(),
            task.getDescription(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            throw invalidTaskTitle();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidTaskTitle();
        }
        if (normalized.length() < GanttTask.TITLE_MIN_LENGTH || normalized.length() > GanttTask.TITLE_MAX_LENGTH) {
            throw invalidTaskTitleLength();
        }
        return normalized;
    }

    private LocalDate normalizeStartDate(LocalDate value) {
        if (value == null) {
            throw invalidTaskStartDate();
        }
        return value;
    }

    private LocalDate normalizeEndDate(LocalDate value) {
        if (value == null) {
            throw invalidTaskEndDate();
        }
        return value;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw invalidTaskDateRange();
        }
    }

    private ResponseStatusException invalidTaskTitle() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task title is required");
    }

    private ResponseStatusException invalidTaskTitleLength() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task title must be between 3 and 60 characters");
    }

    private ResponseStatusException invalidTaskStartDate() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task start date is required");
    }

    private ResponseStatusException invalidTaskEndDate() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task end date is required");
    }

    private ResponseStatusException invalidTaskDateRange() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task end date must be on or after the start date");
    }
}
