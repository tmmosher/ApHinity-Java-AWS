package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.LocationMemberRole;
import com.aphinity.client_analytics_core.api.core.entities.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.LocationUserId;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.LocationMembershipResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for location visibility and membership administration.
 */
@Service
public class LocationService {
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;

    public LocationService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        LocationUserRepository locationUserRepository,
        AccountRoleService accountRoleService
    ) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.locationUserRepository = locationUserRepository;
        this.accountRoleService = accountRoleService;
    }

    /**
     * Returns all locations visible to the user.
     * <p>
     * Partners/admins can view every location; client users only see locations where they
     * have membership.
     *
     * @param userId authenticated user id
     * @return accessible locations
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getAccessibleLocations(Long userId) {
        AppUser user = requireUser(userId);
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return locationRepository.findAllByOrderByNameAsc().stream()
                .map(this::toLocationResponse)
                .toList();
        }

        Map<Long, LocationResponse> uniqueLocations = new LinkedHashMap<>();
        // Defensive de-duplication protects response quality if joins return repeated rows.
        for (LocationUser membership : locationUserRepository.findByUserIdWithLocation(userId)) {
            Location location = membership.getLocation();
            if (location == null || location.getId() == null) {
                continue;
            }
            uniqueLocations.putIfAbsent(location.getId(), toLocationResponse(location));
        }
        return List.copyOf(uniqueLocations.values());
    }

    /**
     * Returns one location if the user has access to it.
     *
     * @param userId authenticated user id
     * @param locationId target location id
     * @return location payload
     */
    @Transactional(readOnly = true)
    public LocationResponse getAccessibleLocation(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }
        return toLocationResponse(location);
    }

    /**
     * Renames a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @param name desired location name
     * @return updated location payload
     */
    @Transactional
    public LocationResponse updateLocationName(Long userId, Long locationId, String name) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);

        String normalizedName = normalizeLocationName(name);
        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        location.setName(normalizedName);

        try {
            locationRepository.saveAndFlush(location);
        } catch (DataIntegrityViolationException ex) {
            throw locationNameInUse();
        }

        return toLocationResponse(location);
    }

    /**
     * Returns memberships for a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return location memberships
     */
    @Transactional(readOnly = true)
    public List<LocationMembershipResponse> getLocationMemberships(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        requirePartnerOrAdmin(user);
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }

        return locationUserRepository.findByLocationIdWithUser(locationId).stream()
            .map(this::toLocationMembershipResponse)
            .toList();
    }

    /**
     * Creates or updates a location membership role for a target user.
     *
     * @param userId authenticated user id performing the change
     * @param locationId target location id
     * @param targetUserId target user id
     * @param role desired membership role
     * @return persisted membership payload
     */
    @Transactional
    public LocationMembershipResponse upsertLocationMembershipRole(
        Long userId,
        Long locationId,
        Long targetUserId,
        LocationMemberRole role
    ) {
        AppUser actingUser = requireUser(userId);
        requirePartnerOrAdmin(actingUser);

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        AppUser targetUser = appUserRepository.findById(targetUserId).orElseThrow(this::targetUserNotFound);

        // Upsert semantics: reuse existing membership when present, otherwise create a new one.
        LocationUser membership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, targetUserId)
            .orElseGet(() -> {
                LocationUser toCreate = new LocationUser();
                toCreate.setId(new LocationUserId(locationId, targetUserId));
                toCreate.setLocation(location);
                toCreate.setUser(targetUser);
                return toCreate;
            });

        membership.setLocation(location);
        membership.setUser(targetUser);
        membership.setUserRole(role);

        LocationUser persisted = locationUserRepository.save(membership);
        return toLocationMembershipResponse(persisted);
    }

    /**
     * Indicates whether a user can access a location.
     *
     * @param userId authenticated user id
     * @param locationId location id
     * @return {@code true} when access is allowed
     */
    @Transactional(readOnly = true)
    public boolean isUserAllowedToAccessLocation(Long userId, Long locationId) {
        AppUser user = requireUser(userId);
        return hasLocationAccess(user, locationId);
    }

    /**
     * Checks access based on account-level role and direct membership.
     */
    private boolean hasLocationAccess(AppUser user, Long locationId) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return true;
        }
        return locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, user.getId());
    }

    /**
     * Maps membership entities into API response shape.
     */
    private LocationMembershipResponse toLocationMembershipResponse(LocationUser membership) {
        AppUser member = membership.getUser();
        String userEmail = member == null ? null : member.getEmail();
        return new LocationMembershipResponse(
            membership.getId().getLocationId(),
            membership.getId().getUserId(),
            userEmail,
            membership.getUserRole(),
            membership.getCreatedAt()
        );
    }

    /**
     * Maps location entities into API response shape.
     */
    private LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(
            location.getId(),
            location.getName(),
            location.getCreatedAt(),
            location.getUpdatedAt()
        );
    }

    /**
     * Trims and validates location names.
     */
    private String normalizeLocationName(String value) {
        if (value == null) {
            throw invalidLocationName();
        }
        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidLocationName();
        }
        return normalized;
    }

    /**
     * Loads the authenticated user or fails with a standard unauthorized error.
     */
    private AppUser requireUser(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);
        return user;
    }

    /**
     * Enforces elevated role requirements for administrative location operations.
     */
    private void requirePartnerOrAdmin(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw forbidden();
        }
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private void requireVerified(AppUser user) {
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
    }

    private ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }

    private ResponseStatusException targetUserNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
    }

    private ResponseStatusException invalidLocationName() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location name is required");
    }

    private ResponseStatusException locationNameInUse() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Location name already in use");
    }
}
