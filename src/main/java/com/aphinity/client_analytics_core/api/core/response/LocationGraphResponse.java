package com.aphinity.client_analytics_core.api.core.response;

import java.time.Instant;

/**
 * Response payload describing a graph assignment to a location.
 *
 * @param locationId location id
 * @param graphId graph id
 * @param createdAt assignment timestamp
 */
public record LocationGraphResponse(
    Long locationId,
    Long graphId,
    Instant createdAt
) {
}
