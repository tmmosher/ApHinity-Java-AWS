package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.location.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.location.UserSubscriptionToLocation;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.requests.dashboard.LocationGraphDataUpdateRequest;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphNameUpdateResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationGraphTemplateFactory;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationThumbnailImageService;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
    private static final String LEGACY_GRAPH_COLOR = "#1f77b4";

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
    private UserSubscriptionToLocationRepository userSubscriptionToLocationRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @Mock
    private LocationThumbnailImageService locationThumbnailImageService;

    @Spy
    private LocationGraphTemplateFactory locationGraphTemplateFactory = new LocationGraphTemplateFactory();

    @Spy
    private LocationGraphUpdatePayloadValidationFactory locationGraphUpdatePayloadValidationFactory =
        new LocationGraphUpdatePayloadValidationFactory();

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
        when(graphRepository.findById(31L)).thenAnswer(invocation -> {
            Graph graph = savedGraphHolder[0];
            if (graph == null) {
                return Optional.empty();
            }

            Graph refreshedGraph = new Graph();
            refreshedGraph.setId(graph.getId());
            refreshedGraph.setName(graph.getName());
            refreshedGraph.setData(graph.getData());
            refreshedGraph.setLayout(graph.getLayout());
            refreshedGraph.setConfig(graph.getConfig());
            refreshedGraph.setStyle(graph.getStyle());
            refreshedGraph.setCreatedAt(graph.getCreatedAt());
            refreshedGraph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00.020Z"));
            return Optional.of(refreshedGraph);
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, 2L, false, "scatter");

        assertEquals(31L, response.id());
        assertEquals("New Plot Graph", response.name());
        assertEquals(1, response.data().size());
        assertEquals("scatter", response.data().getFirst().get("type"));
        assertEquals("Trace 1", response.data().getFirst().get("name"));
        assertEquals(List.of(), response.data().getFirst().get("x"));
        assertEquals(List.of(), response.data().getFirst().get("y"));
        assertEquals(expectedScatterTemplateLayout("Phoenix"), response.layout());
        assertEquals(Map.of("displayModeBar", false, "responsive", false), response.config());
        assertEquals(expectedScatterTemplateStyle(), response.style());
        assertEquals(Instant.parse("2026-01-03T00:00:00.020Z"), response.updatedAt());
        verify(graphRepository).saveAndFlush(any(Graph.class));
        verify(graphRepository).findById(31L);
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals(1, traces.size());
        assertEquals("scatter", traces.getFirst().get("type"));
        assertEquals("Trace 1", traces.getFirst().get("name"));
        assertEquals(List.of(), traces.getFirst().get("x"));
        assertEquals(List.of(), traces.getFirst().get("y"));
        assertEquals(expectedScatterTemplateLayout("Phoenix"), savedGraphHolder[0].getLayout());
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
            Map.of("color", LEGACY_GRAPH_COLOR, "colors", List.of(LEGACY_GRAPH_COLOR)),
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
        assertEquals(Map.of("displayModeBar", false, "responsive", false), response.config());
        assertEquals(expectedPieStyle, response.style());
        assertEquals(expectedPieStyle, savedGraphHolder[0].getStyle());
        assertEquals(Map.of("displayModeBar", false, "responsive", false), savedGraphHolder[0].getConfig());
    }

    @Test
    void createLocationGraphBuildsIndicatorTemplate() {
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
            graph.setId(34L);
            graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            savedGraphHolder[0] = graph;
            return graph;
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, 1L, false, "indicator");

        assertEquals(34L, response.id());
        assertEquals("New Indicator Graph", response.name());
        assertEquals(
            Map.of(
                "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
                "showlegend", false
            ),
            response.layout()
        );
        assertEquals(Map.of("displayModeBar", false, "responsive", false), response.config());
        assertEquals(expectedIndicatorStyle(), response.style());
        assertEquals(expectedIndicatorStyle(), savedGraphHolder[0].getStyle());

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals(1, traces.size());
        assertEquals("indicator", traces.getFirst().get("type"));
        assertEquals("Trace 1", traces.getFirst().get("name"));
        assertEquals("gauge+number", traces.getFirst().get("mode"));
        assertEquals(0L, ((Number) traces.getFirst().get("value")).longValue());
        assertEquals(
            Map.of(
                "suffix", "%",
                "font", Map.of("size", 22L)
            ),
            traces.getFirst().get("number")
        );
        assertEquals(
            Map.of(
                "shape", "angular",
                "axis", Map.of("range", List.of(0L, 100L)),
                "bar", Map.of("color", LEGACY_GRAPH_COLOR),
                "borderwidth", 0L,
                "steps", List.of(
                    Map.of("color", "#80000030", "range", List.of(0L, 30L)),
                    Map.of("color", "#FF000030", "range", List.of(30L, 60L)),
                    Map.of("color", "#FFFF0030", "range", List.of(60L, 90L)),
                    Map.of("color", "#00800030", "range", List.of(90L, 100L))
                ),
                "threshold", Map.of(
                    "line", Map.of("color", "red", "width", 2L),
                    "thickness", new BigDecimal("0.75"),
                    "value", 90L
                )
            ),
            traces.getFirst().get("gauge")
        );
        assertEquals(Map.of("displayModeBar", false, "responsive", false), savedGraphHolder[0].getConfig());
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void createLocationGraphCanonicalizesLineAliasToScatterGraphType() {
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
            graph.setId(35L);
            graph.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
            savedGraphHolder[0] = graph;
            return graph;
        });

        GraphResponse response = locationService.createLocationGraph(5L, 99L, 1L, false, "line");

        assertEquals(35L, response.id());
        assertEquals("scatter", savedGraphHolder[0].getGraphType());
        assertEquals("scatter", response.data().getFirst().get("type"));
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
        assertEquals(
            Map.of(
                "title", Map.of("x", 0.02, "text", "Phoenix", "xanchor", "left"),
                "margin", Map.of("t", 24, "r", 24, "b", 48, "l", 48),
                "showlegend", false
            ),
            response.layout()
        );
        assertEquals(Map.of("displayModeBar", false, "responsive", false), response.config());
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(savedGraphHolder[0].getData());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals("h", traces.getFirst().get("orientation"));
        assertEquals(List.of(), traces.getFirst().get("x"));
        assertEquals(List.of(), traces.getFirst().get("y"));
        assertEquals(Map.of("color", LEGACY_GRAPH_COLOR), traces.getFirst().get("marker"));
        assertEquals(Map.of("displayModeBar", false, "responsive", false), savedGraphHolder[0].getConfig());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(3, sections.size());
        assertEquals(5L, sections.get(2).get("section_id"));
        assertEquals(List.of(45L), sections.get(2).get("graph_ids"));
    }

    private Map<String, Object> expectedIndicatorStyle() {
        return Map.of(
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
    }

    private Map<String, Object> indicatorTrace(int value) {
        return Map.of(
            "type", "indicator",
            "mode", "gauge+number",
            "value", value,
            "number", Map.of(
                "suffix", "%",
                "font", Map.of("size", 22)
            ),
            "gauge", Map.of(
                "shape", "angular",
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", LEGACY_GRAPH_COLOR),
                "borderwidth", 0,
                "steps", List.of(
                    Map.of("color", "#80000030", "range", List.of(0, 30)),
                    Map.of("color", "#FF000030", "range", List.of(30, 60)),
                    Map.of("color", "#FFFF0030", "range", List.of(60, 90)),
                    Map.of("color", "#00800030", "range", List.of(90, 100))
                )
            )
        );
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
    void updateLocationGraphDataRejectsNullGraphDataPayload() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(35L, null))
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph data is invalid", ex.getReason());
        verify(graphRepository, never()).saveAll(anyList());
        verify(locationRepository, never()).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationGraphDataPersistsIndicatorPayloadAndLayout() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(32L);
        graph.setName("Resolution Percent");
        graph.setData(List.of(indicatorTrace(68)));
        graph.setLayout(Map.of("showlegend", false));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        locationService.updateLocationGraphData(
            5L,
            99L,
            List.of(new LocationGraphDataUpdateRequest(
                32L,
                List.of(indicatorTrace(72)),
                Map.of("showlegend", true)
            ))
        );

        verify(graphRepository).saveAll(List.of(graph));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("indicator", traces.getFirst().get("type"));
        assertEquals(72L, ((Number) traces.getFirst().get("value")).longValue());
        assertEquals(Map.of("showlegend", true), graph.getLayout());
    }

    @Test
    void updateLocationGraphDataRejectsOutOfRangeIndicatorPayload() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(33L);
        graph.setName("Resolution Percent");
        graph.setData(List.of(indicatorTrace(68)));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(
                    33L,
                    List.of(indicatorTrace(101))
                ))
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph data is invalid", ex.getReason());
        verify(graphRepository, never()).saveAll(anyList());
        verify(locationRepository, never()).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationGraphDataRejectsMalformedStoredIndicatorGraph() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(34L);
        graph.setName("Resolution Percent");
        graph.setData(List.of(indicatorTrace(101)));

        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(99L), anyCollection()))
            .thenReturn(List.of(graph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(
                    34L,
                    List.of(indicatorTrace(72))
                ))
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph data is invalid", ex.getReason());
        verify(graphRepository, never()).saveAll(anyList());
        verify(locationRepository, never()).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationGraphDataRejectsNonObjectLayoutPayload() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(new LocationGraphDataUpdateRequest(
                    34L,
                    List.of(indicatorTrace(72)),
                    List.of("invalid")
                ))
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Graph data is invalid", ex.getReason());
        verifyNoInteractions(graphRepository);
        verify(locationRepository, never()).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationGraphDataPersistsSectionLayoutWithoutGraphPayloadChanges() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of("sections", List.of())));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(99L)).thenReturn(List.of(locationGraph));

        locationService.updateLocationGraphData(
            5L,
            99L,
            List.of(),
            new LinkedHashMap<>(Map.of(
                "sections",
                List.of(
                    Map.of("section_id", 1, "graph_ids", List.of(31L)),
                    Map.of("section_id", 2, "graph_ids", List.of())
                )
            ))
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(31L), sections.getFirst().get("graph_ids"));
        assertEquals(List.of(), sections.get(1).get("graph_ids"));
        verify(locationRepository).saveAndFlush(location);
        verifyNoInteractions(graphRepository);
    }

    @Test
    void updateLocationGraphDataRejectsStaleSectionLayoutWhenGraphIdsDoNotMatch() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(99L)).thenReturn(true);

        Location location = new Location();
        location.setId(99L);
        location.setName("Phoenix");
        location.setSectionLayout(new LinkedHashMap<>(Map.of("sections", List.of())));
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(99L)).thenReturn(List.of(locationGraph));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.updateLocationGraphData(
                5L,
                99L,
                List.of(),
                new LinkedHashMap<>(Map.of(
                    "sections",
                    List.of(Map.of("section_id", 1, "graph_ids", List.of(999L)))
                ))
            )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Graph update conflict", ex.getReason());
        verify(locationRepository, never()).saveAndFlush(location);
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
        assertEquals("h", traces.getFirst().get("orientation"));
        assertEquals(List.of(9L, 8L, 7L), traces.getFirst().get("x"));
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
        assertEquals("h", traces.get(0).get("orientation"));
        assertEquals(List.of(9L), traces.get(0).get("x"));
        assertEquals("Forecast", traces.get(1).get("name"));
        assertEquals(List.of(12L), traces.get(1).get("x"));
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
    void getAccessibleLocationGraphsReturnsRelationalPayloadWhenDataIsPresent() {
        AppUser user = verifiedUser(17L);
        when(appUserRepository.findById(17L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(44L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(44L, 17L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(41L);
        graph.setName("Relational graph");
        graph.setLayout(Map.of("showlegend", false));
        graph.setConfig(Map.of("displayModeBar", false));
        graph.setStyle(Map.of("height", 280));
        graph.setData(List.of(
            Map.of(
                "type", "pie",
                "labels", List.of("relational"),
                "values", List.of(65)
            )
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
        assertEquals(List.of("relational"), response.data().getFirst().get("labels"));
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
    void getAccessibleLocationGraphsReturnsEmptyDataWhenGraphHasNoTraces() {
        AppUser user = verifiedUser(24L);
        when(appUserRepository.findById(24L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(58L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(58L, 24L)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(89L);
        graph.setName("Empty graph");
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
        assertTrue(response.data().isEmpty());
        assertEquals(Map.of("showlegend", false), response.layout());
        assertEquals(Map.of(), response.config());
        assertEquals(Map.of(), response.style());
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
    void getAccessibleLocationIncludesWorkOrderEmailAndAlertSubscriptionState() {
        AppUser user = verifiedUser(6L);
        when(appUserRepository.findById(6L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(3L);
        location.setName("Austin");
        location.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        location.setSectionLayout(Map.of("sections", List.of()));
        location.setWorkOrderEmail("work-orders@example.com");

        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(userSubscriptionToLocationRepository.existsByLocationIdAndUserId(3L, 6L)).thenReturn(true);

        LocationResponse response = locationService.getAccessibleLocation(6L, 3L);

        assertEquals("work-orders@example.com", response.workOrderEmail());
        assertEquals(true, response.alertsSubscribed());
        verify(locationRepository).findById(3L);
        verify(userSubscriptionToLocationRepository).existsByLocationIdAndUserId(3L, 6L);
    }

    @Test
    void updateLocationWorkOrderEmailPersistsNormalizedEmailForPartnerOrAdmin() {
        AppUser user = verifiedUser(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(9L);
        location.setName("Phoenix");
        location.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        location.setSectionLayout(Map.of("sections", List.of()));
        when(locationRepository.findById(9L)).thenReturn(Optional.of(location));
        when(userSubscriptionToLocationRepository.existsByLocationIdAndUserId(9L, 7L)).thenReturn(false);

        LocationResponse response = locationService.updateLocationWorkOrderEmail(7L, 9L, "  WorkOrders@Example.com  ");

        assertEquals("workorders@example.com", location.getWorkOrderEmail());
        assertEquals("workorders@example.com", response.workOrderEmail());
        assertEquals(false, response.alertsSubscribed());
        assertEquals(false, response.thumbnailAvailable());
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void updateLocationThumbnailPersistsConvertedWebpForPartnerOrAdmin() {
        AppUser user = verifiedUser(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(9L);
        location.setName("Phoenix");
        location.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        location.setSectionLayout(Map.of("sections", List.of()));
        when(locationRepository.findById(9L)).thenReturn(Optional.of(location));
        when(userSubscriptionToLocationRepository.existsByLocationIdAndUserId(9L, 7L)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "thumbnail.png",
            "image/png",
            new byte[] {1, 2, 3}
        );
        byte[] webpBytes = new byte[] {4, 5, 6};
        when(locationThumbnailImageService.convertToWebp(file)).thenReturn(webpBytes);

        LocationResponse response = locationService.updateLocationThumbnail(7L, 9L, file);

        assertArrayEquals(webpBytes, location.getThumbnail());
        assertTrue(response.thumbnailAvailable());
        verify(locationThumbnailImageService).convertToWebp(file);
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void getAccessibleLocationThumbnailReturnsStoredWebpForAuthorizedUser() {
        AppUser user = verifiedUser(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(9L, 7L)).thenReturn(true);

        Location location = new Location();
        location.setId(9L);
        location.setName("Phoenix");
        location.setThumbnail(new byte[] {7, 8, 9});
        when(locationRepository.findById(9L)).thenReturn(Optional.of(location));

        byte[] thumbnail = locationService.getAccessibleLocationThumbnail(7L, 9L);

        assertArrayEquals(new byte[] {7, 8, 9}, thumbnail);
    }

    @Test
    void getAccessibleLocationThumbnailRejectsMissingThumbnail() {
        AppUser user = verifiedUser(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(9L, 7L)).thenReturn(true);

        Location location = new Location();
        location.setId(9L);
        location.setName("Phoenix");
        when(locationRepository.findById(9L)).thenReturn(Optional.of(location));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationService.getAccessibleLocationThumbnail(7L, 9L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location thumbnail not found", ex.getReason());
    }

    @Test
    void subscribeToLocationAlertsCreatesSubscriptionForVerifiedUser() {
        AppUser user = verifiedUser(8L);
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(10L);
        location.setName("Dallas");
        location.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        location.setSectionLayout(Map.of("sections", List.of()));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(userSubscriptionToLocationRepository.findByLocationIdAndUserId(10L, 8L)).thenReturn(Optional.empty());
        when(userSubscriptionToLocationRepository.existsByLocationIdAndUserId(10L, 8L)).thenReturn(true);

        LocationResponse response = locationService.subscribeToLocationAlerts(8L, 10L);

        assertEquals(true, response.alertsSubscribed());
        org.mockito.Mockito.verify(userSubscriptionToLocationRepository).save(
            org.mockito.ArgumentMatchers.argThat(subscription -> {
                if (subscription == null) {
                    return false;
                }
                return subscription.getLocation() == location
                    && subscription.getUserEmail() == user;
            })
        );
    }

    @Test
    void unsubscribeFromLocationAlertsDeletesSubscriptionForVerifiedUser() {
        AppUser user = verifiedUser(9L);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        Location location = new Location();
        location.setId(11L);
        location.setName("Denver");
        location.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        location.setSectionLayout(Map.of("sections", List.of()));
        when(locationRepository.findById(11L)).thenReturn(Optional.of(location));

        UserSubscriptionToLocation subscription = new UserSubscriptionToLocation();
        subscription.setLocation(location);
        subscription.setUserEmail(user);
        when(userSubscriptionToLocationRepository.findByLocationIdAndUserId(11L, 9L)).thenReturn(Optional.of(subscription));
        when(userSubscriptionToLocationRepository.existsByLocationIdAndUserId(11L, 9L)).thenReturn(false);

        LocationResponse response = locationService.unsubscribeFromLocationAlerts(9L, 11L);

        assertEquals(false, response.alertsSubscribed());
        verify(userSubscriptionToLocationRepository).delete(subscription);
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

    private Map<String, Object> expectedScatterTemplateLayout(String locationName) {
        return Map.of(
            "title", Map.of("x", 0.02, "text", locationName, "xanchor", "left"),
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
