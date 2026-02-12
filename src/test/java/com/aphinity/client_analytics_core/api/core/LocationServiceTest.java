package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @InjectMocks
    private LocationService locationService;

    @Test
    void getAccessibleLocationsRejectsUnverifiedUser() {
        AppUser user = new AppUser();
        user.setId(9L);
        user.setEmail("unverified@example.com");
        user.setEmailVerifiedAt(null);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.getAccessibleLocations(9L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
        verifyNoInteractions(locationRepository, locationUserRepository, accountRoleService);
    }
}
