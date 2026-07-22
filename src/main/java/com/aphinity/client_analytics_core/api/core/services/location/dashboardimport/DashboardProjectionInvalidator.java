package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

/** Application-owned mutation port for invalidating cached dashboard projections. */
public interface DashboardProjectionInvalidator {
    void invalidate(Long locationId);
}
