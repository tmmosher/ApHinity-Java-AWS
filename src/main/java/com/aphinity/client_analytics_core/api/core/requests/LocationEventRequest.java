package com.aphinity.client_analytics_core.api.core.requests;

import com.aphinity.client_analytics_core.api.core.entities.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record LocationEventRequest(
    @NotBlank(message = "Event title is required")
    String title,

    @NotNull(message = "Event responsibility is required")
    ServiceEventResponsibility responsibility,

    @NotNull(message = "Event date is required")
    LocalDate date,

    @NotNull(message = "Event time is required")
    LocalTime time,

    String description,

    @NotNull(message = "Event status is required")
    ServiceEventStatus status
) {
}
