package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for updating a single graph's trace data.
 *
 * @param graphId graph identifier associated with the target location
 * @param data raw Plotly trace array/object payload
 */
public record LocationGraphDataUpdateRequest(
    @NotNull
    @Positive
    Long graphId,
    @NotNull
    Object data
) {
}
