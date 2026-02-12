package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.ProfileController;
import com.aphinity.client_analytics_core.api.core.requests.ProfilePasswordUpdateRequest;
import com.aphinity.client_analytics_core.api.core.requests.ProfileUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.ProfileResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {
    @Mock
    private ProfileService profileService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private ProfileController profileController;

    @Test
    void profileReturnsProfileForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .claim("email", "client@example.com")
            .build();
        ProfileResponse expected = new ProfileResponse("Client", "client@example.com", true, AccountRole.CLIENT);
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(profileService.getProfile(42L)).thenReturn(expected);

        ProfileResponse actual = profileController.profile(jwt);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(profileService).getProfile(42L);
    }

    @Test
    void profileRejectsMissingJwt() {
        when(authenticatedUserService.resolveAuthenticatedUserId(null))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> profileController.profile(null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(profileService);
    }

    @Test
    void profileRejectsNonNumericSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("not-a-number")
            .build();

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> profileController.profile(jwt)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(profileService);
    }

    @Test
    void updateProfileDelegatesForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        ProfileUpdateRequest request = new ProfileUpdateRequest("Jane Doe", "JANE@example.com");
        ProfileResponse expected = new ProfileResponse("Jane Doe", "jane@example.com", true, AccountRole.CLIENT);

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(profileService.updateProfile(7L, "Jane Doe", "jane@example.com")).thenReturn(expected);

        ProfileResponse actual = profileController.updateProfile(jwt, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(profileService).updateProfile(7L, "Jane Doe", "jane@example.com");
    }

    @Test
    void updatePasswordDelegatesForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("13")
            .build();
        ProfilePasswordUpdateRequest request = new ProfilePasswordUpdateRequest("old-pass", "new-pass");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(13L);
        profileController.updatePassword(jwt, request);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(profileService).updatePassword(13L, "old-pass", "new-pass");
    }
}
