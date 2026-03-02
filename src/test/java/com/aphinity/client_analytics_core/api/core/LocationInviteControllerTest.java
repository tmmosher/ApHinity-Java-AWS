package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.LocationInviteController;
import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;
import com.aphinity.client_analytics_core.api.core.requests.LocationInviteRequest;
import com.aphinity.client_analytics_core.api.core.response.LocationInviteResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationInviteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationInviteControllerTest {
    @Mock
    private LocationInviteService locationInviteService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private LocationInviteController locationInviteController;

    @Test
    void inviteDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = jwt("7");
        LocationInviteRequest request = new LocationInviteRequest(11L, "client@example.com");
        LocationInviteResponse expected = inviteResponse(91L, 11L, "Austin", "client@example.com", LocationInviteStatus.PENDING);

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(locationInviteService.createInvite(7L, 11L, "client@example.com")).thenReturn(expected);

        LocationInviteResponse actual = locationInviteController.invite(jwt, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationInviteService).createInvite(7L, 11L, "client@example.com");
    }

    @Test
    void inviteableLocationsDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = jwt("7");

        List<LocationResponse> expected = List.of(
            new LocationResponse(
                11L,
                "Austin",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                Map.of("sections", List.of())
            )
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(locationInviteService.getInviteableLocations(7L)).thenReturn(expected);

        List<LocationResponse> actual = locationInviteController.inviteableLocations(jwt);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationInviteService).getInviteableLocations(7L);
    }

    @Test
    void activeInvitesDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = jwt("9");
        List<LocationInviteResponse> expected = List.of(
            inviteResponse(51L, 23L, "Austin", "client@example.com", LocationInviteStatus.PENDING)
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(9L);
        when(locationInviteService.getActiveInvites(9L)).thenReturn(expected);

        List<LocationInviteResponse> actual = locationInviteController.activeInvites(jwt);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationInviteService).getActiveInvites(9L);
    }

    @Test
    void acceptInviteDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = jwt("9");
        LocationInviteResponse expected = inviteResponse(51L, 23L, "Austin", "client@example.com", LocationInviteStatus.ACCEPTED);

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(9L);
        when(locationInviteService.acceptInvite(9L, 51L)).thenReturn(expected);

        LocationInviteResponse actual = locationInviteController.acceptInvite(jwt, 51L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationInviteService).acceptInvite(9L, 51L);
    }

    @Test
    void declineInviteDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = jwt("9");
        LocationInviteResponse expected = inviteResponse(51L, 23L, "Austin", "client@example.com", LocationInviteStatus.REVOKED);

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(9L);
        when(locationInviteService.declineInvite(9L, 51L)).thenReturn(expected);

        LocationInviteResponse actual = locationInviteController.declineInvite(jwt, 51L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationInviteService).declineInvite(9L, 51L);
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject(subject)
            .build();
    }

    private LocationInviteResponse inviteResponse(
        Long id,
        Long locationId,
        String locationName,
        String invitedEmail,
        LocationInviteStatus status
    ) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LocationInviteResponse(
            id,
            locationId,
            locationName,
            invitedEmail,
            7L,
            status,
            now.plusSeconds(3600),
            now,
            status == LocationInviteStatus.ACCEPTED ? now.plusSeconds(60) : null,
            status == LocationInviteStatus.ACCEPTED ? 9L : null,
            status == LocationInviteStatus.REVOKED ? now.plusSeconds(60) : null
        );
    }
}
