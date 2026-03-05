package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for updating a single graph's trace data and optional layout.
 *
 * @param graphId graph identifier associated with the target location
 * @param data raw Plotly trace array/object payload
 * @param layout optional Plotly layout payload
 * @param expectedUpdatedAt optional optimistic-concurrency timestamp for the target graph
 */
public record LocationGraphDataUpdateRequest(
    @NotNull
    @Positive
    Long graphId,
    @NotNull
    Object data,
    Object layout,
    String expectedUpdatedAt
) {
    public LocationGraphDataUpdateRequest(Long graphId, Object data) {
        this(graphId, data, null, null);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object layout) {
        this(graphId, data, layout, null);
    }
}
