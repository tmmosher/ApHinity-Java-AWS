package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LocationDashboardTimeRangeScheduler {
    private final LocationDashboardRefreshService refreshService;
    private final LocationRepository locationRepository;

    public LocationDashboardTimeRangeScheduler(
        LocationDashboardRefreshService refreshService,
        LocationRepository locationRepository
    ) {
        this.refreshService = refreshService;
        this.locationRepository = locationRepository;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "America/Phoenix")
    public void refreshAllLocationDashboardTimeRanges() {
        for (Location location : locationRepository.findAllByOrderByNameAsc()) {
            if (location == null || location.getId() == null) {
                continue;
            }
            refreshService.refreshDerivedGraphs(location.getId());
        }
    }
}
