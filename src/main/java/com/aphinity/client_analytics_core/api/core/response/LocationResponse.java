package com.aphinity.client_analytics_core.api.core.response;

import java.time.Instant;
import java.util.Map;

/**
 * Serialized location payload.
 *
 * @param id location id
 * @param name location display name
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param sectionLayout dashboard section ordering metadata
 */
public record LocationResponse(
    Long id,
    String name,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> sectionLayout
) {
}
