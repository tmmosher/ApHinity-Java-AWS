package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;

import java.util.Map;

/** Application-owned read port for range-aware dashboard projections. */
public interface DashboardProjectionQuery {
    Map<Long, LocationDashboardTimeRangeService.MonthRangeGraphProjection> resolveGraphProjections(
        Long locationId,
        DashboardGraphMonthRange monthRange
    );

    LocationDashboardTablePageResponse resolveTablePage(
        Long locationId,
        Long graphId,
        Integer monthRange,
        Integer page,
        Integer size
    );
}
