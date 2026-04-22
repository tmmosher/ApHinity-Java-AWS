package com.aphinity.client_analytics_core.api.core.response.location;

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
 * @param workOrderEmail work-order submission email
 * @param alertsSubscribed whether the current user is subscribed to location alerts
 */
public record LocationResponse(
    Long id,
    String name,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> sectionLayout,
    String workOrderEmail,
    Boolean alertsSubscribed
) {
    public LocationResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> sectionLayout
    ) {
        this(id, name, createdAt, updatedAt, sectionLayout, null, null);
    }
}
