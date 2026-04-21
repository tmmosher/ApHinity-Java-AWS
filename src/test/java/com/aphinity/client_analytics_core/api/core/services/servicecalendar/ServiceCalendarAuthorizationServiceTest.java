package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCalendarAuthorizationServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private AccountRoleService accountRoleService;

    private ServiceCalendarAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new ServiceCalendarAuthorizationService(
            appUserRepository,
            locationRepository,
            locationUserRepository,
            accountRoleService
        );
    }

    @Test
    void requireCreateCorrectiveActionPermissionAllowsClientForClientSourceEvents() {
        AppUser user = user(5L);
        ServiceEvent sourceEvent = sourceEvent(ServiceEventResponsibility.CLIENT);

        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(99L, 5L)).thenReturn(true);

        assertDoesNotThrow(() -> authorizationService.requireCreateCorrectiveActionPermission(user, 99L, sourceEvent));
    }

    @Test
    void requireCreateCorrectiveActionPermissionAllowsPartnerUsersRegardlessOfSourceEventResponsibility() {
        AppUser user = user(5L);
        ServiceEvent sourceEvent = sourceEvent(ServiceEventResponsibility.PARTNER);

        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        assertDoesNotThrow(() -> authorizationService.requireCreateCorrectiveActionPermission(user, 99L, sourceEvent));
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private ServiceEvent sourceEvent(ServiceEventResponsibility responsibility) {
        ServiceEvent sourceEvent = new ServiceEvent();
        sourceEvent.setResponsibility(responsibility);
        return sourceEvent;
    }
}
