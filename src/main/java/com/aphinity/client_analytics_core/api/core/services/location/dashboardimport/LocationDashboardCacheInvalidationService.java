package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.stereotype.Service;

/** Mutation-side boundary for invalidating location dashboard projections. */
@Service
public class LocationDashboardCacheInvalidationService implements DashboardProjectionInvalidator {
    private final LocationDashboardTimeRangeService engine;

    public LocationDashboardCacheInvalidationService(LocationDashboardTimeRangeService engine) {
        this.engine = engine;
    }

    @Override
    public void invalidate(Long locationId) {
        engine.invalidateLocationCache(locationId);
    }
}
