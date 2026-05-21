package com.aphinity.client_analytics_core.api.core.requests.dashboard;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for updating a single graph's trace data and optional layout.
 *
 * @param graphId graph identifier associated with the target location
 * @param data raw Plotly trace array/object payload
 * @param timeRangeData optional rolling-window Plotly trace payloads keyed by response time-range id
 * @param layout optional Plotly layout payload
 * @param expectedUpdatedAt optional optimistic-concurrency timestamp for the target graph
 */
public record LocationGraphDataUpdateRequest(
    @NotNull
    @Positive
    Long graphId,
    @NotNull
    Object data,
    Object timeRangeData,
    Object layout,
    String expectedUpdatedAt
) {
    public LocationGraphDataUpdateRequest(Long graphId, Object data) {
        this(graphId, data, null, null, null);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object layout) {
        this(graphId, data, null, layout, null);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object layout, String expectedUpdatedAt) {
        this(graphId, data, null, layout, expectedUpdatedAt);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object timeRangeData, Object layout) {
        this(graphId, data, timeRangeData, layout, null);
    }
}
