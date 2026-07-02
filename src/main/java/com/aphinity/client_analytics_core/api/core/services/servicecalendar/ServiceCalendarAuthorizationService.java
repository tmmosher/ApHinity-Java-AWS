package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralizes authorization for service calendar event operations.
 * <p>
 * Partners/admins can manage all service events for readable locations. Client
 * users can read member locations and create/update/complete only client-owned
 * events; deletes remain partner/admin-only.
 */
@Service
public class ServiceCalendarAuthorizationService {
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;

    public ServiceCalendarAuthorizationService(
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

    public void requireReadableLocationAccess(AppUser user, Long locationId) {
        requireLocationExists(locationId);
        if (!hasLocationAccess(user, locationId)) {
            throw forbidden();
        }
    }

    public Location requireLocation(Long locationId) {
        return locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
    }

    public void requireLocationExists(Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw locationNotFound();
        }
    }

    /**
     * Verifies that the user can create an event with the requested responsibility.
     *
     * @param user actor entity
     * @param locationId target location id
     * @param responsibility requested event responsibility
     */
    public void requireCreatePermission(
        AppUser user,
        Long locationId,
        ServiceEventResponsibility responsibility
    ) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        if (responsibility == ServiceEventResponsibility.CLIENT && hasLocationAccess(user, locationId)) {
            return;
        }
        throw forbidden();
    }

    /**
     * Verifies corrective-action creation rights against the source event.
     * Client users may only create corrective actions for client-responsibility
     * source events at locations they can access.
     *
     * @param user actor entity
     * @param locationId target location id
     * @param sourceEvent persisted source event
     */
    public void requireCreateCorrectiveActionPermission(
        AppUser user,
        Long locationId,
        ServiceEvent sourceEvent
    ) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        if (sourceEvent.getResponsibility() == ServiceEventResponsibility.CLIENT && hasLocationAccess(user, locationId)) {
            return;
        }
        throw forbidden();
    }

    /**
     * Verifies that the user can update an existing event into the requested
     * responsibility state.
     *
     * @param user actor entity
     * @param locationId target location id
     * @param serviceEvent existing event
     * @param responsibility requested replacement responsibility
     */
    public void requireUpdatePermission(
        AppUser user,
        Long locationId,
        ServiceEvent serviceEvent,
        ServiceEventResponsibility responsibility
    ) {
        if (accountRoleService.isPartnerOrAdmin(user)) {
            return;
        }
        if (serviceEvent.getResponsibility() == ServiceEventResponsibility.CLIENT
            && responsibility == ServiceEventResponsibility.CLIENT
            && hasLocationAccess(user, locationId)
        ) {
            return;
        }
        throw forbidden();
    }

    public void requireDeletePermission(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw forbidden();
        }
    }

    public boolean isPartnerOrAdmin(AppUser user) {
        return accountRoleService.isPartnerOrAdmin(user);
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
