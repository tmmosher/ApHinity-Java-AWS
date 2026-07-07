package com.aphinity.client_analytics_core.api.core.response.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GraphResponse(
    Long id,
    String name,
    String description,
    List<Map<String, Object>> data,
    Map<String, Object> layout,
    Map<String, Object> config,
    Map<String, Object> style,
    Instant createdAt,
    Instant updatedAt
) {
    public GraphResponse(
        Long id,
        String name,
        List<Map<String, Object>> data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, name, null, data, layout, config, style, createdAt, updatedAt);
    }
}
