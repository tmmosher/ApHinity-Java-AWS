package com.aphinity.client_analytics_core.api.core.requests.servicecalendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ServiceCalendarBulkEventRequest(
    @NotEmpty(message = "Service calendar events are required")
    List<@Valid ServiceCalendarBulkEventRowRequest> events
) {
}
