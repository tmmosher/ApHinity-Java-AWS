package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import org.springframework.stereotype.Component;

/** Maps location persistence state to the public API representation. */
@Component
public class LocationResponseMapper {
    private final UserSubscriptionToLocationRepository subscriptionRepository;

    public LocationResponseMapper(UserSubscriptionToLocationRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public LocationResponse toResponse(Location location) {
        return toResponse(location, null);
    }

    public LocationResponse toResponse(Location location, AppUser user) {
        Boolean alertsSubscribed = user == null ? null
            : subscriptionRepository.existsByLocationIdAndUserId(location.getId(), user.getId());
        byte[] thumbnail = location.getThumbnail();
        return new LocationResponse(
            location.getId(),
            location.getName(),
            location.getCreatedAt(),
            location.getUpdatedAt(),
            location.getSectionLayout(),
            location.getWorkOrderEmail(),
            alertsSubscribed,
            thumbnail != null && thumbnail.length > 0
        );
    }
}
