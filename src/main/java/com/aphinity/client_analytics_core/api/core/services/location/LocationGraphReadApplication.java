package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;

import java.util.List;

/** Read boundary for a location's complete graph collection. */
public interface LocationGraphReadApplication {
    List<GraphResponse> getAccessibleLocationGraphs(Long userId, Long locationId, Integer monthRange);
}
