package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.location.LocationController;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateBatchRequest;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphCreateRequest;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateRequest;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphNameUpdateRequest;
import com.aphinity.client_analytics_core.api.core.requests.location.LocationRequest;
import com.aphinity.client_analytics_core.api.core.requests.location.LocationWorkOrderEmailUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphNameUpdateResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
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
            List.of(Map.of("type", "bar", "name", "Conversion")),
            Map.of("showlegend", false),
            Map.of("displayModeBar", false),
            Map.of("height", 260),
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
    void createLocationDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationRequest request = new LocationRequest("Phoenix");
        LocationResponse expected = new LocationResponse(
            19L,
            "Phoenix",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Map.of("sections", List.of())
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.createLocation(42L, "Phoenix")).thenReturn(expected);

        LocationResponse actual = locationController.createLocation(jwt, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).createLocation(42L, "Phoenix");
    }

    @Test
    void deleteMembershipDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);

        locationController.deleteMembership(jwt, 8L, 13L);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).deleteLocationMembership(42L, 8L, 13L);
    }

    @Test
    void updateLocationGraphDataDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGraphDataUpdateBatchRequest request = new LocationGraphDataUpdateBatchRequest(
            List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar", "y", List.of(2, 4, 6)))))
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);

        locationController.updateLocationGraphData(jwt, 8L, request);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).updateLocationGraphData(42L, 8L, request.graphs());
    }

    @Test
    void updateLocationGraphDataDelegatesSectionLayoutUpdatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGraphDataUpdateBatchRequest request = new LocationGraphDataUpdateBatchRequest(
            List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar", "y", List.of(2, 4, 6))))),
            Map.of(
                "sections",
                List.of(Map.of("section_id", 1, "graph_ids", List.of(31L)))
            )
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);

        locationController.updateLocationGraphData(jwt, 8L, request);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).updateLocationGraphData(42L, 8L, request.graphs(), request.sectionLayout());
    }

    @Test
    void createLocationGraphDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGraphCreateRequest request = new LocationGraphCreateRequest(null, true, "scatter");
        GraphResponse expected = new GraphResponse(
            55L,
            "New Plot Graph",
            scatterGraphData(),
            scatterGraphLayout(),
            Map.of("displayModeBar", false, "responsive", false),
            scatterGraphStyle(),
            Instant.parse("2026-01-03T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z")
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.createLocationGraph(42L, 8L, null, true, "scatter")).thenReturn(expected);

        GraphResponse actual = locationController.createLocationGraph(jwt, 8L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).createLocationGraph(42L, 8L, null, true, "scatter");
    }

    @Test
    void deleteLocationGraphDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);

        locationController.deleteLocationGraph(jwt, 8L, 31L);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).deleteLocationGraph(42L, 8L, 31L);
    }

    @Test
    void updateLocationGraphNameDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGraphNameUpdateRequest request = new LocationGraphNameUpdateRequest("Renamed graph");
        GraphNameUpdateResponse expected = new GraphNameUpdateResponse(
            31L,
            "Renamed graph",
            Instant.parse("2026-01-03T00:00:00Z")
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.updateLocationGraphName(42L, 8L, 31L, "Renamed graph")).thenReturn(expected);

        GraphNameUpdateResponse actual = locationController.updateLocationGraphName(jwt, 8L, 31L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).updateLocationGraphName(42L, 8L, 31L, "Renamed graph");
    }

    @Test
    void updateLocationWorkOrderEmailDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationWorkOrderEmailUpdateRequest request = new LocationWorkOrderEmailUpdateRequest("work-orders@example.com");
        LocationResponse expected = new LocationResponse(
            8L,
            "Dallas",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"),
            Map.of("sections", List.of()),
            "work-orders@example.com",
            true
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.updateLocationWorkOrderEmail(42L, 8L, "work-orders@example.com")).thenReturn(expected);

        LocationResponse actual = locationController.updateLocationWorkOrderEmail(jwt, 8L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).updateLocationWorkOrderEmail(42L, 8L, "work-orders@example.com");
    }

    @Test
    void subscribeToLocationAlertsDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationResponse expected = new LocationResponse(
            8L,
            "Dallas",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"),
            Map.of("sections", List.of()),
            null,
            true
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.subscribeToLocationAlerts(42L, 8L)).thenReturn(expected);

        LocationResponse actual = locationController.subscribeToLocationAlerts(jwt, 8L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).subscribeToLocationAlerts(42L, 8L);
    }

    @Test
    void unsubscribeFromLocationAlertsDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationResponse expected = new LocationResponse(
            8L,
            "Dallas",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-03T00:00:00Z"),
            Map.of("sections", List.of()),
            null,
            false
        );
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationService.unsubscribeFromLocationAlerts(42L, 8L)).thenReturn(expected);

        LocationResponse actual = locationController.unsubscribeFromLocationAlerts(jwt, 8L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationService).unsubscribeFromLocationAlerts(42L, 8L);
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

    private static List<Map<String, Object>> scatterGraphData() {
        return List.of(
            Map.of(
                "type", "scatter",
                "name", "Trace 1",
                "x", List.of(),
                "y", List.of(),
                "line", Map.of(
                    "color", "#1f77b4",
                    "width", 2
                ),
                "mode", "lines+markers",
                "marker", Map.of("size", 6)
            )
        );
    }

    private static Map<String, Object> scatterGraphLayout() {
        return Map.of(
            "title", Map.of("x", 0.02, "text", "Phoenix", "xanchor", "left"),
            "xaxis", Map.of("type", "date", "tickformat", "%b %Y"),
            "yaxis", Map.of("range", List.of(0, 100), "title", "% Compliance", "ticksuffix", "%"),
            "legend", Map.of("x", 0, "y", -0.3, "orientation", "h"),
            "margin", Map.of("b", 60, "l", 50, "r", 20, "t", 50)
        );
    }

    private static Map<String, Object> scatterGraphStyle() {
        return Map.of(
            "theme", Map.of(
                "dark", Map.of(
                    "gridColor", "rgba(148, 163, 184, 0.3)",
                    "textColor", "#e5e7eb"
                ),
                "light", Map.of(
                    "gridColor", "rgba(15, 23, 42, 0.15)",
                    "textColor", "#111827"
                )
            ),
            "height", 320
        );
    }
}
