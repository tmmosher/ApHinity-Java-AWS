package com.aphinity.client_analytics_core.api.core.response.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;

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
    Instant createdAt,
    Instant updatedAt
) {
}
