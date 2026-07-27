package com.aphinity.client_analytics_core.api.core.response.dashboard;

import java.util.List;

/** Section-scoped graph resource returned for a finite dashboard time range. */
public record LocationSectionGraphsResponse(
    Long sectionId,
    Integer monthRange,
    List<GraphResponse> graphs,
    List<Long> missingGraphIds
) {
    public LocationSectionGraphsResponse {
        graphs = graphs == null ? List.of() : List.copyOf(graphs);
        missingGraphIds = missingGraphIds == null ? List.of() : List.copyOf(missingGraphIds);
    }
}
