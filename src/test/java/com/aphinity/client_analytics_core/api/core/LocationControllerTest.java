package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.LocationController;
import com.aphinity.client_analytics_core.api.core.plotly.PlotlyGraphSpec;
import com.aphinity.client_analytics_core.api.core.response.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {
    @Mock
    private LocationService locationService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private LocationController locationController;

    @Test
    void locationGraphsDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();

        GraphResponse graphResponse = new GraphResponse(
            31L,
            "Weekly conversion",
            new PlotlyGraphSpec(),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z")
        );
        List<GraphResponse> expected = List.of(graphResponse);
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(locationService.getAccessibleLocationGraphs(7L, 14L)).thenReturn(expected);

        List<GraphResponse> actual = locationController.locationGraphs(jwt, 14L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).getAccessibleLocationGraphs(7L, 14L);
    }

    @Test
    void locationDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();

        LocationResponse expected = new LocationResponse(
            8L,
            "Dallas",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"),
            Map.of("sections", List.of())
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.getAccessibleLocation(42L, 8L)).thenReturn(expected);

        LocationResponse actual = locationController.location(jwt, 8L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).getAccessibleLocation(42L, 8L);
    }

    @Test
    void locationGraphsRejectsMissingJwt() {
        when(authenticatedUserService.resolveAuthenticatedUserId(null))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> locationController.locationGraphs(null, 14L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(locationService);
    }
}
