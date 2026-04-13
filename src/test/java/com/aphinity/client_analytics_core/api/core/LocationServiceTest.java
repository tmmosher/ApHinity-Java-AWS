package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateRequest;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphNameUpdateResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private LocationGraphRepository locationGraphRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @InjectMocks
    private LocationService locationService;

    @Test
    void createLocationRejectsPartnerUser() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.PARTNER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.createLocation(5L, "Phoenix")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository);
    }

    @Test
    void createLocationPersistsNormalizedLocationForAdmin() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.ADMIN);

        LocationResponse response = locationService.createLocation(5L, "  Phoenix  ");

        verify(locationRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(location ->
            "Phoenix".equals(location.getName())
        ));
        assertEquals("Phoenix", response.name());
    }

    @Test
    void createLocationRejectsDuplicateName() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.ADMIN);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
            .when(locationRepository)
            .saveAndFlush(org.mockito.ArgumentMatchers.any(Location.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.createLocation(5L, "Phoenix")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Location name already in use", ex.getReason());
    }

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
    void deleteLocationMembershipRejectsUnknownAuthenticatedUser() {
        when(appUserRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 11L, 12L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid authenticated user", ex.getReason());
        verifyNoInteractions(locationRepository, locationUserRepository, accountRoleService);
    }

    @Test
    void deleteLocationMembershipRejectsUnverifiedAuthenticatedUser() {
        AppUser user = unverifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 11L, 12L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
        verifyNoInteractions(locationRepository, locationUserRepository, accountRoleService);
    }

    @Test
    void deleteLocationMembershipRejectsCallerWithoutElevatedRole() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 11L, 12L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, locationUserRepository);
    }

    @Test
    void deleteLocationMembershipRejectsMissingLocation() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(99L, 12L)).thenReturn(Optional.empty());
        when(locationRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 99L, 12L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location not found", ex.getReason());
        verify(locationRepository).existsById(99L);
        verify(appUserRepository, never()).existsById(12L);
        verify(locationUserRepository).findByIdLocationIdAndIdUserId(99L, 12L);
    }

    @Test
    void deleteLocationMembershipRejectsMissingTargetUser() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(99L, 12L)).thenReturn(Optional.empty());
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(appUserRepository.existsById(12L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 99L, 12L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Target user not found", ex.getReason());
        verify(locationUserRepository).findByIdLocationIdAndIdUserId(99L, 12L);
    }

    @Test
    void deleteLocationMembershipRejectsMissingMembership() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(appUserRepository.existsById(12L)).thenReturn(true);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(99L, 12L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationMembership(5L, 99L, 12L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location membership not found", ex.getReason());
    }

    @Test
    void deleteLocationMembershipDeletesExistingMembership() {
        AppUser user = verifiedUser(5L);
        LocationUser membership = new LocationUser();
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(99L, 12L)).thenReturn(Optional.of(membership));

        locationService.deleteLocationMembership(5L, 99L, 12L);

        verify(locationUserRepository).delete(membership);
        verify(locationRepository, never()).existsById(99L);
        verify(appUserRepository, never()).existsById(12L);
    }

    @Test
    void updateLocationGraphDataRejectsUnknownAuthenticatedUser() {
        when(appUserRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                11L,
                List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"))))
            )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid authenticated user", ex.getReason());
        verifyNoInteractions(locationRepository, graphRepository);
    }

    @Test
    void updateLocationGraphNameRejectsCallerWithoutElevatedRole() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphName(5L, 11L, 31L, "Renamed graph")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, graphRepository);
    }

    @Test
    void updateLocationGraphNameRejectsBlankName() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(99L, 31L)).thenReturn(Optional.of(graph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphName(5L, 99L, 31L, "   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph name is required", ex.getReason());
        verify(graphRepository, never()).saveAndFlush(graph);
    }

    @Test
    void updateLocationGraphNamePersistsDedicatedRename() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(99L, 31L)).thenReturn(Optional.of(graph));
        when(graphRepository.saveAndFlush(graph)).thenReturn(graph);

        GraphNameUpdateResponse response = locationService.updateLocationGraphName(5L, 99L, 31L, "Renamed graph");

        assertEquals(31L, response.graphId());
        assertEquals("Renamed graph", response.name());
        assertEquals("Renamed graph", graph.getName());
        verify(graphRepository).saveAndFlush(graph);
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void deleteLocationGraphRejectsCallerWithoutElevatedRole() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.deleteLocationGraph(5L, 99L, 31L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, graphRepository, locationGraphRepository);
    }

    @Test
    void deleteLocationGraphRemovesGraphFromSectionLayoutAndDeletesGraph() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of(
            "sections",
            List.of(
                Map.of("section_id", 1, "graph_ids", List.of(31L, 44L)),
                Map.of("section_id", 2, "graph_ids", List.of(57L))
            )
        )));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(99L, 31L)).thenReturn(Optional.of(graph));
        when(locationGraphRepository.findByIdGraphId(31L)).thenReturn(List.of());

        locationService.deleteLocationGraph(5L, 99L, 31L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(44L), sections.get(0).get("graph_ids"));
        assertEquals(List.of(57L), sections.get(1).get("graph_ids"));
        verify(locationGraphRepository).deleteById(new LocationGraphId(99L, 31L));
        verify(graphRepository).delete(graph);
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void createLocationGraphRejectsCallerWithoutElevatedRole() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.createLocationGraph(5L, 99L, 2L, false, "bar")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, graphRepository, locationGraphRepository);
    }

    @Test
    void createLocationGraphRejectsUnknownSection() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of(
            "sections",
            List.of(Map.of("section_id", 1, "graph_ids", List.of(11L)))
        ));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.createLocationGraph(5L, 99L, 2L, false, "bar")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Location section not found", ex.getReason());
        verify(graphRepository, never()).saveAndFlush(any(Graph.class));
        verifyNoInteractions(locationGraphRepository);
    }

    @Test
    void createLocationGraphPersistsGraphAndAppendsItToRequestedSection() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of(
            "sections",
            List.of(
                Map.of("section_id", 1, "graph_ids", List.of(11L)),
                Map.of("section_id", 2, "graph_ids", List.of())
            )
        )));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph[] savedGraphHolder = new Graph[1];
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(31L);
            graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            savedGraphHolder[0] = graph;
            return graph;
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, 2L, false, "scatter");

        assertEquals(31L, response.id());
        assertEquals("New Plot Graph", response.name());
        assertEquals(1, response.data().size());
        assertEquals("scatter", response.data().getFirst().get("type"));
        assertEquals("Trace 1", response.data().getFirst().get("name"));
        assertEquals(List.of(), response.data().getFirst().get("x"));
        assertEquals(List.of(), response.data().getFirst().get("y"));
        assertEquals(expectedScatterTemplateLayout(), response.layout());
        assertEquals(Map.of("displayModeBar", false, "responsive", false), response.config());
        assertEquals(expectedScatterTemplateStyle(), response.style());
        verify(graphRepository).saveAndFlush(any(Graph.class));
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals(1, traces.size());
        assertEquals("scatter", traces.getFirst().get("type"));
        assertEquals("Trace 1", traces.getFirst().get("name"));
        assertEquals(List.of(), traces.getFirst().get("x"));
        assertEquals(List.of(), traces.getFirst().get("y"));
        assertEquals(expectedScatterTemplateLayout(), savedGraphHolder[0].getLayout());
        assertEquals(Map.of("displayModeBar", false, "responsive", false), savedGraphHolder[0].getConfig());
        assertEquals(expectedScatterTemplateStyle(), savedGraphHolder[0].getStyle());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(11L), sections.get(0).get("graph_ids"));
        assertEquals(List.of(31L), sections.get(1).get("graph_ids"));
    }

    @Test
    void createLocationGraphBuildsDonutPieTemplate() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of(
            "sections",
            List.of(Map.of("section_id", 1, "graph_ids", List.of()))
        )));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph[] savedGraphHolder = new Graph[1];
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(33L);
            graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            savedGraphHolder[0] = graph;
            return graph;
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, 1L, false, "pie");

        assertEquals(33L, response.id());
        assertEquals("New Pie Graph", response.name());
        Map<String, Object> expectedPieStyle = Map.of(
            "theme",
            Map.of(
                "dark", Map.of(
                    "gridColor", "rgba(148, 163, 184, 0.3)",
                    "textColor", "#e5e7eb"
                ),
                "light", Map.of(
                    "gridColor", "rgba(15, 23, 42, 0.15)",
                    "textColor", "#111827"
                )
            ),
            "height", 160
        );

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals(1, traces.size());
        assertEquals("pie", traces.getFirst().get("type"));
        assertEquals(0.72, ((Number) traces.getFirst().get("hole")).doubleValue());
        assertEquals(false, traces.getFirst().get("sort"));
        assertEquals(List.of("fill"), traces.getFirst().get("labels"));
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) traces.getFirst().get("values");
        assertEquals(1, values.size());
        assertEquals(0L, ((Number) values.getFirst()).longValue());
        assertEquals("none", traces.getFirst().get("textinfo"));
        assertEquals("clockwise", traces.getFirst().get("direction"));
        assertEquals("%{label}: %{value}<extra></extra>", traces.getFirst().get("hovertemplate"));
        assertEquals(
            Map.of("color", "#2563eb", "colors", List.of("#2563eb")),
            traces.getFirst().get("marker")
        );
        assertEquals(
            Map.of(
                "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
                "showlegend", false,
                "annotations", List.of(Map.of(
                    "x", 0.5,
                    "y", 0.5,
                    "text", "<b>0</b>",
                    "xref", "paper",
                    "yref", "paper",
                    "showarrow", false,
                    "font", Map.of("size", 22)
                ))
            ),
            response.layout()
        );
        assertEquals(expectedPieStyle, response.style());
        assertEquals(expectedPieStyle, savedGraphHolder[0].getStyle());
    }

    @Test
    void createLocationGraphCreatesAndAppendsANewSectionWhenRequested() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of(
            "sections",
            List.of(
                Map.of("section_id", 2, "graph_ids", List.of(11L)),
                Map.of("section_id", 4, "graph_ids", List.of(12L))
            )
        )));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph[] savedGraphHolder = new Graph[1];
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(45L);
            graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            savedGraphHolder[0] = graph;
            return graph;
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, null, true, "bar");

        assertEquals(45L, response.id());
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals(List.of(), traces.getFirst().get("x"));
        assertEquals(List.of(), traces.getFirst().get("y"));
        assertEquals(Map.of("color", "#2563eb"), traces.getFirst().get("marker"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(3, sections.size());
        assertEquals(5L, sections.get(2).get("section_id"));
        assertEquals(List.of(45L), sections.get(2).get("graph_ids"));
    }

    @Test
    void updateLocationGraphDataRejectsCallerWithoutElevatedRole() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                11L,
                List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"))))
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, graphRepository);
    }

    @Test
    void updateLocationGraphDataRejectsMissingLocation() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"))))
            )
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location not found", ex.getReason());
        verifyNoInteractions(graphRepository);
    }

    @Test
    void updateLocationGraphDataAllowsEmptyUpdates() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        locationService.updateLocationGraphData(5L, 99L, List.of());

        verifyNoInteractions(graphRepository);
        verify(locationRepository, never()).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationGraphDataRejectsDuplicateGraphIds() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(
                    new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"))),
                    new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "scatter")))
                )
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph update list contains duplicate graph ids", ex.getReason());
        verifyNoInteractions(graphRepository);
    }

    @Test
    void updateLocationGraphDataRejectsUnknownLocationGraph() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"))))
            )
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location graph not found", ex.getReason());
    }

    @Test
    void updateLocationGraphDataRejectsInvalidGraphPayload() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Invalid test graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(31L, List.of(Map.of("type", "bar"), "bad")))
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph data is invalid", ex.getReason());
        verify(graphRepository, never()).saveAll(anyList());
    }

    @Test
    void updateLocationGraphDataUpdatesTraceDataAndLayout() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setLayout(Map.of("title", "Original layout"));
        graph.setConfig(Map.of("displayModeBar", false));
        graph.setStyle(Map.of("height", 240));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        locationService.updateLocationGraphData(
            5L,
            99L,
            List.of(new LocationGraphDataUpdateRequest(
                31L,
                List.of(Map.of("type", "bar", "y", List.of(9, 8, 7))),
                Map.of("title", "Updated layout")
            ))
        );

        verify(graphRepository).saveAll(List.of(graph));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals(List.of(9L, 8L, 7L), traces.getFirst().get("y"));
        assertEquals(Map.of("title", "Updated layout"), graph.getLayout());
        assertEquals(Map.of("displayModeBar", false), graph.getConfig());
        assertEquals(Map.of("height", 240), graph.getStyle());
    }

    @Test
    void updateLocationGraphDataSupportsAddingAndRenamingTraces() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "name", "Current", "y", List.of(1, 2, 3))));
        graph.setLayout(Map.of("title", "Original layout"));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        locationService.updateLocationGraphData(
            5L,
            99L,
            List.of(new LocationGraphDataUpdateRequest(
                31L,
                List.of(
                    Map.of("type", "bar", "name", "Actual", "x", List.of("Jan"), "y", List.of(9)),
                    Map.of("type", "bar", "name", "Forecast", "x", List.of("Feb"), "y", List.of(12))
                )
            ))
        );

        verify(graphRepository).saveAll(List.of(graph));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(2, traces.size());
        assertEquals("Actual", traces.get(0).get("name"));
        assertEquals(List.of(9L), traces.get(0).get("y"));
        assertEquals("Forecast", traces.get(1).get("name"));
        assertEquals(List.of(12L), traces.get(1).get("y"));
        assertEquals(Map.of("title", "Original layout"), graph.getLayout());
    }

    @Test
    void updateLocationGraphDataRejectsStaleExpectedUpdatedAt() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setUpdatedAt(Instant.parse("2026-01-05T00:00:00Z"));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(
                    31L,
                    List.of(Map.of("type", "bar", "y", List.of(9, 8, 7))),
                    null,
                    "2026-01-04T00:00:00Z"
                ))
            )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Graph update conflict", ex.getReason());
        verify(graphRepository, never()).saveAll(anyList());
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

    private AppUser unverifiedUser(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("unverified@example.com");
        user.setEmailVerifiedAt(null);
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

    private Map<String, Object> expectedScatterTemplateLayout() {
        return Map.of(
            "title", Map.of("x", 0.02, "text", "", "xanchor", "left"),
            "xaxis", Map.of("type", "date", "tickformat", "%b %Y"),
            "yaxis", Map.of("range", List.of(0, 100), "title", "% Compliance", "ticksuffix", "%"),
            "legend", Map.of("x", 0, "y", -0.3, "orientation", "h"),
            "margin", Map.of("b", 60, "l", 50, "r", 20, "t", 50)
        );
    }

    private Map<String, Object> expectedScatterTemplateStyle() {
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
