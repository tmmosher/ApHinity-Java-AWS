package com.aphinity.client_analytics_core.api.core.response;

import com.aphinity.client_analytics_core.api.core.plotly.PlotlyGraphSpec;

import java.time.Instant;

public record GraphResponse(
    Long id,
    String name,
    Long ownerId,
    PlotlyGraphSpec data,
    Instant createdAt,
    Instant updatedAt
) {
}
