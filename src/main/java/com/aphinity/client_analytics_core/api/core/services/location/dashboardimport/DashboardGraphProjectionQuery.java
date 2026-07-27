package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;

import java.util.Collection;
import java.util.Map;

/** Read port dedicated to range-aware graph projections. */
public interface DashboardGraphProjectionQuery {
    Map<Long, DashboardGraphProjection> resolveGraphProjections(
        Long locationId,
        DashboardGraphMonthRange monthRange
    );

    Map<Long, DashboardGraphProjection> resolveGraphProjections(
        Long locationId,
        Collection<Long> graphIds,
        DashboardGraphMonthRange monthRange
    );
}
