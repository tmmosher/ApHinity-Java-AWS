package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.location.LocationController;
import com.aphinity.client_analytics_core.api.core.requests.location.LocationRequest;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationDetailsService;
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
class LocationControllerTest {
    @Mock LocationDetailsService service;
    @Mock AuthenticatedUserService authenticatedUserService;
    @InjectMocks LocationController controller;

    @Test
    void locationDelegatesToFocusedService() {
        Jwt jwt = jwt();
        LocationResponse expected = location(8L, "Dallas");
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(service.getAccessibleLocation(42L, 8L)).thenReturn(expected);

        assertSame(expected, controller.location(jwt, 8L));
        verify(service).getAccessibleLocation(42L, 8L);
    }

    @Test
    void createDelegatesToFocusedService() {
        Jwt jwt = jwt();
        LocationResponse expected = location(9L, "Phoenix");
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(service.createLocation(42L, "Phoenix")).thenReturn(expected);

        assertSame(expected, controller.createLocation(jwt, new LocationRequest("Phoenix")));
        verify(service).createLocation(42L, "Phoenix");
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("42").build();
    }

    private LocationResponse location(Long id, String name) {
        return new LocationResponse(
            id, name, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"),
            Map.of("sections", List.of()), null, null, false
        );
    }
}
