package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.Optional;

/** Application-owned port for selecting the import module for a location. */
public interface DashboardImportStrategyResolver {
    Optional<LocationDashboardImportStrategy> resolve(String locationName);
}
