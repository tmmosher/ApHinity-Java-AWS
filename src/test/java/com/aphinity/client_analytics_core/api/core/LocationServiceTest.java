package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Graph;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.LocationGraph;
import com.aphinity.client_analytics_core.api.core.repositories.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationGraphRepository locationGraphRepository;

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

    @Test
    void getAccessibleLocationGraphsReturnsMappedGraphsForAuthorizedUser() {
        AppUser user = verifiedUser(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(11L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(11L, 7L)).thenReturn(true);

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("type", "bar");
        trace.put("name", "Sessions");
        trace.put("y", List.of(4, 9, 6));

        Graph graph = new Graph();
        graph.setId(19L);
        graph.setName("Daily sessions");
        graph.setData(trace);
        graph.setLayout(Map.of("title", "Sessions"));
        graph.setConfig(Map.of("displayModeBar", false));
        graph.setStyle(Map.of("height", 320));
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(11L)).thenReturn(List.of(locationGraph));

        List<GraphResponse> responses = locationService.getAccessibleLocationGraphs(7L, 11L);

        assertEquals(1, responses.size());
        GraphResponse response = responses.getFirst();
        assertEquals(19L, response.id());
        assertEquals("Daily sessions", response.name());
        assertEquals(1, response.data().size());
        assertEquals("bar", response.data().getFirst().get("type"));
        assertEquals(Map.of("title", "Sessions"), response.layout());
        assertEquals(Map.of("displayModeBar", false), response.config());
        assertEquals(Map.of("height", 320), response.style());
        verify(locationGraphRepository).findByLocationIdWithGraph(11L);
    }

    @Test
    void getAccessibleLocationGraphsNormalizesLegacyNestedGraphPayload() {
        AppUser user = verifiedUser(17L);
        when(appUserRepository.findById(17L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(44L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(44L, 17L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(41L);
        graph.setName("Legacy graph");
        setRawGraphData(graph, Map.of(
            "data", List.of(
                Map.of(
                    "type", "pie",
                    "labels", List.of("fill", "rest"),
                    "values", List.of(65, 35)
                )
            ),
            "layout", Map.of("showlegend", false),
            "config", Map.of("displayModeBar", false),
            "style", Map.of("height", 280)
        ));
        graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-04T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(44L)).thenReturn(List.of(locationGraph));

        List<GraphResponse> responses = locationService.getAccessibleLocationGraphs(17L, 44L);

        assertEquals(1, responses.size());
        GraphResponse response = responses.getFirst();
        assertEquals(1, response.data().size());
        assertEquals("pie", response.data().getFirst().get("type"));
        assertEquals(Map.of("showlegend", false), response.layout());
        assertEquals(Map.of("displayModeBar", false), response.config());
        assertEquals(Map.of("height", 280), response.style());
    }

    @Test
    void getAccessibleLocationGraphsSupportsStoredTraceArrays() {
        AppUser user = verifiedUser(23L);
        when(appUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(57L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(57L, 23L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(88L);
        graph.setName("Two-trace graph");
        graph.setData(List.of(
            Map.of("type", "scatter", "name", "baseline"),
            Map.of("type", "bar", "name", "actual")
        ));
        graph.setCreatedAt(Instant.parse("2026-01-05T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-06T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(57L)).thenReturn(List.of(locationGraph));

        List<GraphResponse> responses = locationService.getAccessibleLocationGraphs(23L, 57L);

        assertEquals(1, responses.size());
        GraphResponse response = responses.getFirst();
        assertEquals(2, response.data().size());
        assertEquals("scatter", response.data().get(0).get("type"));
        assertEquals("bar", response.data().get(1).get("type"));
        assertNull(response.layout());
        assertNull(response.config());
        assertNull(response.style());
    }

    @Test
    void getAccessibleLocationGraphsParsesJsonTextPayloadsFromDatabase() {
        AppUser user = verifiedUser(24L);
        when(appUserRepository.findById(24L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(58L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(58L, 24L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(89L);
        graph.setName("String payload graph");
        setRawGraphData(
            graph,
            "{\"type\":\"pie\",\"labels\":[\"fill\",\"rest\"],\"values\":[65,35]}"
        );
        graph.setLayout(Map.of("showlegend", false));
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setCreatedAt(Instant.parse("2026-01-05T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-06T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(58L)).thenReturn(List.of(locationGraph));

        List<GraphResponse> responses = locationService.getAccessibleLocationGraphs(24L, 58L);

        assertEquals(1, responses.size());
        GraphResponse response = responses.getFirst();
        assertEquals(1, response.data().size());
        assertEquals("pie", response.data().getFirst().get("type"));
        assertEquals(List.of("fill", "rest"), response.data().getFirst().get("labels"));
        assertEquals(List.of(65L, 35L), response.data().getFirst().get("values"));
        assertEquals(Map.of("showlegend", false), response.layout());
        assertEquals(Map.of(), response.config());
        assertEquals(Map.of(), response.style());
    }

    @Test
    void getAccessibleLocationGraphsRejectsMalformedStoredGraphData() {
        AppUser user = verifiedUser(31L);
        when(appUserRepository.findById(31L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(66L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(66L, 31L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(101L);
        graph.setName("Malformed graph");
        setRawGraphData(graph, List.of(Map.of("type", "bar"), "bad-entry"));
        graph.setCreatedAt(Instant.parse("2026-01-07T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-08T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(66L)).thenReturn(List.of(locationGraph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.getAccessibleLocationGraphs(31L, 66L)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Graph payload is invalid", ex.getReason());
    }

    @Test
    void getAccessibleLocationsReturnsAllLocationsForPartnerOrAdmin() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location first = new Location();
        first.setId(1L);
        first.setName("Austin");
        first.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        first.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        first.setSectionLayout(Map.of("sections", List.of()));

        Location second = new Location();
        second.setId(2L);
        second.setName("Denver");
        second.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
        second.setUpdatedAt(Instant.parse("2026-01-04T00:00:00Z"));
        second.setSectionLayout(Map.of("sections", List.of()));

        when(locationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(first, second));

        List<LocationResponse> responses = locationService.getAccessibleLocations(5L);

        assertEquals(2, responses.size());
        assertEquals(List.of("Austin", "Denver"), responses.stream().map(LocationResponse::name).toList());
        verify(locationRepository).findAllByOrderByNameAsc();
        verifyNoInteractions(locationUserRepository);
    }

    @Test
    void getAccessibleLocationGraphsRejectsUnauthorizedUser() {
        AppUser user = verifiedUser(8L);
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(12L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(12L, 8L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.getAccessibleLocationGraphs(8L, 12L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationGraphRepository);
    }

    @Test
    void getAccessibleLocationGraphsRejectsMissingLocation() {
        AppUser user = verifiedUser(9L);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(33L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.getAccessibleLocationGraphs(9L, 33L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location not found", ex.getReason());
        verifyNoInteractions(accountRoleService, locationUserRepository, locationGraphRepository);
    }

    private AppUser verifiedUser(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("verified@example.com");
        user.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private void setRawGraphData(Graph graph, Object rawData) {
        try {
            var field = Graph.class.getDeclaredField("data");
            field.setAccessible(true);
            field.set(graph, rawData);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to set raw graph data for legacy payload test", ex);
        }
    }
}
