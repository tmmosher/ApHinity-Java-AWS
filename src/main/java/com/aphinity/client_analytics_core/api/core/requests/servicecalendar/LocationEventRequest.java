package com.aphinity.client_analytics_core.api.core.requests.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record LocationEventRequest(
    @NotBlank(message = "Event title is required")
    @Size(max = ServiceEvent.TITLE_MAX_LENGTH, message = "Event title must be 42 characters or fewer")
    String title,

    @NotNull(message = "Event responsibility is required")
    ServiceEventResponsibility responsibility,

    @NotNull(message = "Event date is required")
    LocalDate date,

    @NotNull(message = "Event time is required")
    LocalTime time,

    LocalDate endDate,

    LocalTime endTime,

    String description,

    @NotNull(message = "Event status is required")
    ServiceEventStatus status
) {
}
