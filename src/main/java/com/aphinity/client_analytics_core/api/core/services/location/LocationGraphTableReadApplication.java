package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;

/** Read boundary for pageable dashboard table projections. */
public interface LocationGraphTableReadApplication {
    LocationDashboardTablePageResponse getAccessibleLocationGraphTablePage(
        Long userId, Long locationId, Long graphId, Integer monthRange, Integer page, Integer size
    );
}
