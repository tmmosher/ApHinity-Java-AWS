package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationInviteRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.LocationInviteService;
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
class LocationInviteServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationInviteRepository locationInviteRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @InjectMocks
    private LocationInviteService locationInviteService;

    @Test
    void getActiveInvitesRejectsUnverifiedUser() {
        AppUser user = new AppUser();
        user.setId(15L);
        user.setEmail("unverified@example.com");
        user.setEmailVerifiedAt(null);
        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.getActiveInvites(15L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
        verifyNoInteractions(locationRepository, locationInviteRepository, locationUserRepository, accountRoleService);
    }
}
