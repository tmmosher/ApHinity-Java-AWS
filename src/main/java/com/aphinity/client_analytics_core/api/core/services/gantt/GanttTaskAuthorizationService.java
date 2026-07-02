package com.aphinity.client_analytics_core.api.core.services.gantt;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralizes authorization decisions for location-scoped Gantt task operations.
 * <p>
 * Read access follows general location visibility: partner/admin users can read
 * every location, while client users must be members of the location. Write
 * access is intentionally stricter and limited to partner/admin users.
 */
@Service
public class GanttTaskAuthorizationService {
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;

    public GanttTaskAuthorizationService(
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
     * Loads the authenticated user and rejects unverified accounts.
     *
     * @param userId authenticated user id from the JWT subject
     * @return verified user entity
     */
    public AppUser requireUser(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);
        return user;
    }

    /**
     * Verifies that a location exists and the user may read it.
     *
     * @param user verified user entity
     * @param locationId target location id
     */
    public void requireReadableLocationAccess(AppUser user, Long locationId) {
        requireLocationExists(locationId);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }
    }

    /**
     * Loads a location or raises a location-scoped 404.
     *
     * @param locationId target location id
     * @return location entity
     */
    public Location requireLocation(Long locationId) {
        return locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
    }

    public void requireLocationExists(Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
    }

    /**
     * Verifies that the user can mutate Gantt tasks for the location.
     *
     * @param user verified user entity
     * @param locationId target location id
     */
    public void requireWritePermission(AppUser user, Long locationId) {
        requireReadableLocationAccess(user, locationId);
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        throw forbidden();
    }

    private void requireVerified(AppUser user) {
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
    }

    private boolean hasLocationAccess(AppUser user, Long locationId) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return true;
        }
        return locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, user.getId());
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
    }

    private ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }
}
