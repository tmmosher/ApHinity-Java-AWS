package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Batch request for graph data updates scoped to a single location.
 *
 * @param graphs graph update rows
 */
public record LocationGraphDataUpdateBatchRequest(
    @NotNull
    List<@Valid LocationGraphDataUpdateRequest> graphs
) {
}
