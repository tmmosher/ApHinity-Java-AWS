package com.aphinity.client_analytics_core.api.core.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GraphResponse(
    Long id,
    String name,
    List<Map<String, Object>> data,
    Map<String, Object> layout,
    Map<String, Object> config,
    Map<String, Object> style,
    Instant createdAt,
    Instant updatedAt
) {
}
