package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Graph;
import com.aphinity.client_analytics_core.api.core.entities.LocationGraph;
import com.aphinity.client_analytics_core.api.core.repositories.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.GraphResponse;
import com.aphinity.client_analytics_core.api.core.plotly.PlotlyGraphSpec;
import com.aphinity.client_analytics_core.api.core.plotly.PlotlyTrace;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        PlotlyTrace trace = new PlotlyTrace();
        trace.setType("bar");
        trace.setName("Sessions");
        PlotlyGraphSpec graphSpec = new PlotlyGraphSpec();
        graphSpec.setData(List.of(trace));

        Graph graph = new Graph();
        graph.setId(19L);
        graph.setName("Daily sessions");
        graph.setData(graphSpec);
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
        assertSame(graphSpec, response.data());
        assertEquals("bar", response.data().getData().getFirst().getType());
        verify(locationGraphRepository).findByLocationIdWithGraph(11L);
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
}
