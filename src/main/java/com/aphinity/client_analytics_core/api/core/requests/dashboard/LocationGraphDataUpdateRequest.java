package com.aphinity.client_analytics_core.api.core.requests.dashboard;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * Request payload for updating a single graph's trace data and graph-table metadata.
 *
 * @param graphId graph identifier associated with the target location
 * @param data optional raw Plotly trace array/object payload
 * @param layout optional Plotly layout payload
 * @param config optional Plotly config payload
 * @param style optional application-specific graph style payload
 * @param expectedUpdatedAt optional optimistic-concurrency timestamp for the target graph
 */
public record LocationGraphDataUpdateRequest(
    @NotNull
    @Positive
    Long graphId,
    Object data,
    Map<String, Object> layout,
    Map<String, Object> config,
    Map<String, Object> style,
    String expectedUpdatedAt
) {
    public LocationGraphDataUpdateRequest(Long graphId, Object data) {
        this(graphId, data, null, null, null, null);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object layout) {
        this(graphId, data, layout, null);
    }

    public LocationGraphDataUpdateRequest(Long graphId, Object data, Object layout, String expectedUpdatedAt) {
        this(
            graphId,
            data,
            layout instanceof Map<?, ?> layoutMap ? copyMap(layoutMap) : null,
            null,
            null,
            expectedUpdatedAt
        );
    }

    private static Map<String, Object> copyMap(Map<?, ?> rawMap) {
        return rawMap.entrySet().stream()
            .filter(entry -> entry.getKey() instanceof String)
            .collect(
                java.util.stream.Collectors.toMap(
                    entry -> (String) entry.getKey(),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    java.util.LinkedHashMap::new
                )
            );
    }
}
