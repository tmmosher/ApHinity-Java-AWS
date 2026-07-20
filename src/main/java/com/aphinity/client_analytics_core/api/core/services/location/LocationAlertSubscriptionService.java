package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.location.UserSubscriptionToLocation;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the authenticated user's alert subscription for a location. */
@Service
public class LocationAlertSubscriptionService {
    private final LocationRepository locationRepository;
    private final UserSubscriptionToLocationRepository subscriptionRepository;
    private final LocationAccessPolicy accessPolicy;
    private final LocationResponseMapper responseMapper;

    public LocationAlertSubscriptionService(
        LocationRepository locationRepository,
        UserSubscriptionToLocationRepository subscriptionRepository,
        LocationAccessPolicy accessPolicy,
        LocationResponseMapper responseMapper
    ) {
        this.locationRepository = locationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public LocationResponse subscribe(Long userId, Long locationId) {
        AppUser user = accessPolicy.requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        if (!accessPolicy.hasLocationAccess(user, locationId)) throw accessPolicy.forbidden();
        UserSubscriptionToLocation subscription = subscriptionRepository.findByLocationIdAndUserId(locationId, userId)
            .orElseGet(UserSubscriptionToLocation::new);
        subscription.setLocation(location);
        subscription.setUserEmail(user);
        subscriptionRepository.save(subscription);
        return responseMapper.toResponse(location, user);
    }

    @Transactional
    public LocationResponse unsubscribe(Long userId, Long locationId) {
        AppUser user = accessPolicy.requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        if (!accessPolicy.hasLocationAccess(user, locationId)) throw accessPolicy.forbidden();
        subscriptionRepository.findByLocationIdAndUserId(locationId, userId).ifPresent(subscriptionRepository::delete);
        return responseMapper.toResponse(location, user);
    }
}
