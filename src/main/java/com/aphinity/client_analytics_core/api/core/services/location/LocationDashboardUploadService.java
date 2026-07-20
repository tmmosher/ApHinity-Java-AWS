package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardSpreadsheetUploadResponse;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCacheInvalidationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Authorized application boundary for dashboard spreadsheet uploads. */
@Service
public class LocationDashboardUploadService {
    private final LocationRepository locationRepository;
    private final LocationAccessPolicy accessPolicy;
    private final LocationDashboardImportService importService;
    private final LocationDashboardCacheInvalidationService cacheInvalidationService;

    public LocationDashboardUploadService(
        LocationRepository locationRepository,
        LocationAccessPolicy accessPolicy,
        LocationDashboardImportService importService,
        LocationDashboardCacheInvalidationService cacheInvalidationService
    ) {
        this.locationRepository = locationRepository;
        this.accessPolicy = accessPolicy;
        this.importService = importService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public LocationDashboardSpreadsheetUploadResponse upload(
        Long userId, Long locationId, MultipartFile file, boolean persistSamples, Integer monthRange
    ) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        if (!DashboardGraphMonthRange.fromRequestValue(monthRange).isAllTime()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dashboard spreadsheets can only be uploaded from All Data");
        }
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        LocationDashboardSpreadsheetUploadResponse response = importService.importLocationDashboard(location, file, persistSamples);
        cacheInvalidationService.invalidate(locationId);
        return response;
    }
}
