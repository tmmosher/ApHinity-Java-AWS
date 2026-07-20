package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUserId;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.location.LocationMembershipResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Owns administrative membership reads and mutations. */
@Service
public class LocationMembershipService {
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final LocationAccessPolicy accessPolicy;

    public LocationMembershipService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        LocationUserRepository locationUserRepository,
        LocationAccessPolicy accessPolicy
    ) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.locationUserRepository = locationUserRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public List<LocationMembershipResponse> getMemberships(Long userId, Long locationId) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        accessPolicy.requireLocationExists(locationId);
        return locationUserRepository.findByLocationIdWithUser(locationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public LocationMembershipResponse upsertMembership(Long userId, Long locationId, Long targetUserId) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        Location location = locationRepository.findById(locationId).orElseThrow(accessPolicy::locationNotFound);
        AppUser target = appUserRepository.findById(targetUserId).orElseThrow(this::targetUserNotFound);
        LocationUser membership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, targetUserId)
            .orElseGet(() -> {
                LocationUser created = new LocationUser();
                created.setId(new LocationUserId(locationId, targetUserId));
                return created;
            });
        membership.setLocation(location);
        membership.setUser(target);
        return toResponse(locationUserRepository.save(membership));
    }

    @Transactional
    public void deleteMembership(Long userId, Long locationId, Long targetUserId) {
        AppUser user = accessPolicy.requireUser(userId);
        accessPolicy.requirePartnerOrAdmin(user);
        LocationUser membership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, targetUserId).orElse(null);
        if (membership != null) {
            locationUserRepository.delete(membership);
            return;
        }
        accessPolicy.requireLocationExists(locationId);
        if (!appUserRepository.existsById(targetUserId)) throw targetUserNotFound();
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location membership not found");
    }

    private LocationMembershipResponse toResponse(LocationUser membership) {
        AppUser user = membership.getUser();
        return new LocationMembershipResponse(
            membership.getId().getLocationId(), membership.getId().getUserId(),
            user == null ? null : user.getEmail(), membership.getCreatedAt()
        );
    }

    private ResponseStatusException targetUserNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
    }
}
