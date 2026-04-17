package com.aphinity.client_analytics_core.api.core.response.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ServiceEventResponse(
    Long id,
    String title,
    ServiceEventResponsibility responsibility,
    LocalDate date,
    LocalTime time,
    LocalDate endDate,
    LocalTime endTime,
    String description,
    ServiceEventStatus status,
    @JsonProperty("isCorrectiveAction")
    boolean correctiveAction,
    Long correctiveActionSourceEventId,
    String correctiveActionSourceEventTitle,
    Instant createdAt,
    Instant updatedAt
) {
}
