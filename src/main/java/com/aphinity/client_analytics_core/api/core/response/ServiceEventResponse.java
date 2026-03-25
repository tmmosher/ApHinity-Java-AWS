package com.aphinity.client_analytics_core.api.core.response;

import com.aphinity.client_analytics_core.api.core.entities.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventStatus;

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
