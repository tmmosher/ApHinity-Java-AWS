package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;

/** Read port dedicated to pageable dashboard table projections. */
public interface DashboardTableProjectionQuery {
    LocationDashboardTablePageResponse resolveTablePage(
        Long locationId,
        Long graphId,
        Integer monthRange,
        Integer page,
        Integer size
    );
}
