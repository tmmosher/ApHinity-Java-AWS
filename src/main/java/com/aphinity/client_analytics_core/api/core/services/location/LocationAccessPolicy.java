package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Shared authentication and location-authorization policy for location-scoped services. */
@Service
public class LocationAccessPolicy {
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;

    public LocationAccessPolicy(
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

    public AppUser requireUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user"));
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
        return user;
    }

    public void requireLocationExists(Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
    }

    public void requireLocationAccess(AppUser user, Long locationId) {
        requireLocationExists(locationId);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }
    }

    public boolean hasLocationAccess(AppUser user, Long locationId) {
        return accountRoleService.isPartnerOrAdmin(user)
            || locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, user.getId());
    }

    public boolean isPartnerOrAdmin(AppUser user) {
        return accountRoleService.isPartnerOrAdmin(user);
    }

    public void requirePartnerOrAdmin(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw forbidden();
        }
    }

    public void requireAdmin(AppUser user) {
        if (accountRoleService.resolveAccountRole(user) != AccountRole.ADMIN) {
            throw forbidden();
        }
    }

    public ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
    }

    public ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }
}
