package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardSpreadsheetUploadResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardMutationLockService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardTimeRangeService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardProjectionService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardRefreshService;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCacheInvalidationService;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Source-compatibility facade for callers migrating from the former aggregate
 * service. It is deliberately not a Spring bean; production controllers inject
 * the focused services directly.
 */
@Deprecated(forRemoval = true)
public class LocationService extends LocationGraphService {
    private final LocationDetailsService detailsService;
    private final LocationThumbnailService thumbnailService;
    private final LocationDashboardUploadService uploadService;
    private final LocationAlertSubscriptionService alertService;
    private final LocationMembershipService membershipService;

    public LocationService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        GraphRepository graphRepository,
        LocationGraphRepository locationGraphRepository,
        LocationUserRepository locationUserRepository,
        UserSubscriptionToLocationRepository subscriptionRepository,
        AccountRoleService accountRoleService,
        LocationThumbnailImageService thumbnailImageService,
        LocationGraphTemplateFactory graphTemplateFactory,
        LocationGraphUpdatePayloadValidationFactory payloadValidationFactory,
        LocationDashboardImportService importService,
        LocationDashboardMutationLockService mutationLockService,
        LocationDashboardTimeRangeService timeRangeService,
        GraphResponseMapper graphResponseMapper
    ) {
        super(
            appUserRepository, locationRepository, graphRepository, locationGraphRepository,
            locationUserRepository, accountRoleService, graphTemplateFactory, payloadValidationFactory,
            mutationLockService,
            new LocationDashboardProjectionService(timeRangeService),
            new LocationDashboardRefreshService(timeRangeService),
            new LocationDashboardCacheInvalidationService(timeRangeService),
            graphResponseMapper
        );
        LocationAccessPolicy accessPolicy = new LocationAccessPolicy(
            appUserRepository, locationRepository, locationUserRepository, accountRoleService
        );
        LocationResponseMapper responseMapper = new LocationResponseMapper(subscriptionRepository);
        this.detailsService = new LocationDetailsService(
            locationRepository, locationUserRepository, accessPolicy, responseMapper,
            new LocationDashboardCacheInvalidationService(timeRangeService)
        );
        this.thumbnailService = new LocationThumbnailService(
            locationRepository, thumbnailImageService, accessPolicy, responseMapper
        );
        this.uploadService = new LocationDashboardUploadService(
            locationRepository, accessPolicy, importService, new LocationDashboardCacheInvalidationService(timeRangeService)
        );
        this.alertService = new LocationAlertSubscriptionService(
            locationRepository, subscriptionRepository, accessPolicy, responseMapper
        );
        this.membershipService = new LocationMembershipService(
            appUserRepository, locationRepository, locationUserRepository, accessPolicy
        );
    }

    public List<LocationResponse> getAccessibleLocations(Long userId) {
        return detailsService.getAccessibleLocations(userId);
    }

    public LocationResponse getAccessibleLocation(Long userId, Long locationId) {
        return detailsService.getAccessibleLocation(userId, locationId);
    }

    public LocationResponse createLocation(Long userId, String name) {
        return detailsService.createLocation(userId, name);
    }

    public LocationResponse updateLocationName(Long userId, Long locationId, String name) {
        return detailsService.updateLocationName(userId, locationId, name);
    }

    public LocationResponse updateLocationWorkOrderEmail(Long userId, Long locationId, String email) {
        return detailsService.updateWorkOrderEmail(userId, locationId, email);
    }

    public LocationResponse updateLocationThumbnail(Long userId, Long locationId, MultipartFile file) {
        return thumbnailService.updateThumbnail(userId, locationId, file);
    }

    public byte[] getAccessibleLocationThumbnail(Long userId, Long locationId) {
        return thumbnailService.getThumbnail(userId, locationId);
    }

    public LocationDashboardSpreadsheetUploadResponse uploadLocationDashboardSpreadsheet(
        Long userId, Long locationId, MultipartFile file
    ) {
        return uploadService.upload(userId, locationId, file, false, null);
    }

    public LocationDashboardSpreadsheetUploadResponse uploadLocationDashboardSpreadsheet(
        Long userId, Long locationId, MultipartFile file, boolean persistSamples
    ) {
        return uploadService.upload(userId, locationId, file, persistSamples, null);
    }

    public LocationDashboardSpreadsheetUploadResponse uploadLocationDashboardSpreadsheet(
        Long userId, Long locationId, MultipartFile file, boolean persistSamples, Integer monthRange
    ) {
        return uploadService.upload(userId, locationId, file, persistSamples, monthRange);
    }

    public LocationResponse subscribeToLocationAlerts(Long userId, Long locationId) {
        return alertService.subscribe(userId, locationId);
    }

    public LocationResponse unsubscribeFromLocationAlerts(Long userId, Long locationId) {
        return alertService.unsubscribe(userId, locationId);
    }

    public List<LocationMembershipResponse> getLocationMemberships(Long userId, Long locationId) {
        return membershipService.getMemberships(userId, locationId);
    }

    public LocationMembershipResponse upsertLocationMembership(Long userId, Long locationId, Long targetUserId) {
        return membershipService.upsertMembership(userId, locationId, targetUserId);
    }

    public void deleteLocationMembership(Long userId, Long locationId, Long targetUserId) {
        membershipService.deleteMembership(userId, locationId, targetUserId);
    }
}
