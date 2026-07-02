package com.aphinity.client_analytics_core.api.core.requests.dashboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.List;

/**
 * Batch request for graph updates scoped to a single location.
 *
 * @param graphs graph update rows
 * @param sectionLayout optional dashboard section layout update
 * @param monthRange dashboard month range active when the update was submitted
 */
public record LocationGraphDataUpdateBatchRequest(
    @NotNull
    List<@Valid LocationGraphDataUpdateRequest> graphs,
    Map<String, Object> sectionLayout,
    Integer monthRange
) {
    public LocationGraphDataUpdateBatchRequest(List<@Valid LocationGraphDataUpdateRequest> graphs) {
        this(graphs, null, null);
    }

    public LocationGraphDataUpdateBatchRequest(
        List<@Valid LocationGraphDataUpdateRequest> graphs,
        Map<String, Object> sectionLayout
    ) {
        this(graphs, sectionLayout, null);
    }
}
