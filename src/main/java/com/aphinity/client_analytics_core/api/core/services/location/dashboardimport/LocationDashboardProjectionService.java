package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Read-side boundary for finite-range graph and table projections. */
@Service
public class LocationDashboardProjectionService implements DashboardProjectionQuery {
    private final LocationDashboardTimeRangeService engine;

    public LocationDashboardProjectionService(LocationDashboardTimeRangeService engine) {
        this.engine = engine;
    }

    @Override
    public Map<Long, LocationDashboardTimeRangeService.MonthRangeGraphProjection> resolveGraphProjections(
        Long locationId, DashboardGraphMonthRange monthRange
    ) {
        return engine.resolveLocationMonthRangeProjections(locationId, monthRange);
    }

    @Override
    public LocationDashboardTablePageResponse resolveTablePage(
        Long locationId, Long graphId, Integer monthRange, Integer page, Integer size
    ) {
        return engine.resolveRecentSampleMeasurementsPage(locationId, graphId, monthRange, page, size);
    }
}
