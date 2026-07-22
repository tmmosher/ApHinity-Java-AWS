package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.stereotype.Service;

/** Write-side boundary for rebuilding persisted dashboard-derived graph state. */
@Service
public class LocationDashboardRefreshService implements DerivedGraphRefresher {
    private final LocationDashboardTimeRangeService engine;

    public LocationDashboardRefreshService(LocationDashboardTimeRangeService engine) {
        this.engine = engine;
    }

    @Override
    public void refreshDerivedGraphs(Long locationId) {
        engine.refreshLocationDateGroups(locationId);
    }

    @Override
    public void refreshImportedGraphRanges(Long locationId) {
        engine.refreshLocationImportedGraphDateGroups(locationId);
    }
}
