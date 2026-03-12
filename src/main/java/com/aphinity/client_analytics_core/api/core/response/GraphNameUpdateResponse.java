package com.aphinity.client_analytics_core.api.core.response;

import java.time.Instant;

/**
 * Response payload returned after a graph rename succeeds.
 *
 * @param graphId renamed graph identifier
 * @param name persisted graph display name
 * @param updatedAt server timestamp for the rename mutation
 */
public record GraphNameUpdateResponse(
    Long graphId,
    String name,
    Instant updatedAt
) {
}
