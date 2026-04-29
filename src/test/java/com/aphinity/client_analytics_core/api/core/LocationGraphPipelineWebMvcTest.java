package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.security.AccessTokenRefreshFilter;
import com.aphinity.client_analytics_core.api.core.controllers.location.LocationController;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.UserSubscriptionToLocationRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationGraphTemplateFactory;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationThumbnailImageService;
import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdatePayloadValidationFactory;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.core.MethodParameter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = LocationController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = AccessTokenRefreshFilter.class
    )
)
@Import({
    LocationGraphTemplateFactory.class,
    LocationGraphUpdatePayloadValidationFactory.class,
    LocationService.class,
    LocationGraphPipelineWebMvcTest.JwtArgumentResolverConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
class LocationGraphPipelineWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @MockitoBean
    private LocationRepository locationRepository;

    @MockitoBean
    private GraphRepository graphRepository;

    @MockitoBean
    private LocationGraphRepository locationGraphRepository;

    @MockitoBean
    private LocationUserRepository locationUserRepository;

    @MockitoBean
    private UserSubscriptionToLocationRepository userSubscriptionToLocationRepository;

    @MockitoBean
    private AccountRoleService accountRoleService;

    @MockitoBean
    private LocationThumbnailImageService locationThumbnailImageService;

    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;

    @MockitoBean
    private AsyncLogService asyncLogService;

    @Test
    void locationGraphsCoversRepositoryLookupDataFormationAndClientTransmission() throws Exception {
        Long userId = 7L;
        Long locationId = 11L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(locationId)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, userId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(31L);
        graph.setName("Fill ratio");
        graph.setData(Map.of(
            "hole", 0.72,
            "sort", false,
            "type", "pie",
            "labels", List.of("fill", "rest"),
            "marker", Map.of("colors", List.of("blue", "gray")),
            "values", List.of(65, 35),
            "textinfo", "none",
            "direction", "clockwise",
            "hovertemplate", "%{label}: %{value}<extra></extra>"
        ));
        graph.setLayout(Map.of(
            "margin", Map.of("b", 10, "l", 10, "r", 10, "t", 10),
            "showlegend", false,
            "annotations", List.of(Map.of(
                "x", 0.5,
                "y", 0.5,
                "text", "<b>Test</b>",
                "xref", "paper",
                "yref", "paper",
                "showarrow", false
            ))
        ));
        graph.setConfig(Map.of("displayModeBar", false));
        graph.setStyle(Map.of("height", 240));
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(31))
            .andExpect(jsonPath("$[0].name").value("Fill ratio"))
            .andExpect(jsonPath("$[0].data[0].type").value("pie"))
            .andExpect(jsonPath("$[0].data[0].labels[0]").value("fill"))
            .andExpect(jsonPath("$[0].data[0].values[1]").value(35))
            .andExpect(jsonPath("$[0].layout.showlegend").value(false))
            .andExpect(jsonPath("$[0].layout.annotations[0].text").value("<b>Test</b>"))
            .andExpect(jsonPath("$[0].config.displayModeBar").value(false))
            .andExpect(jsonPath("$[0].style.height").value(240));

        verify(locationRepository).existsById(locationId);
        verify(locationUserRepository).existsByIdLocationIdAndIdUserId(locationId, userId);
        verify(locationGraphRepository).findByLocationIdWithGraph(locationId);
    }

    @Test
    void locationGraphsCoversIndicatorDataFormationAndClientTransmission() throws Exception {
        Long userId = 9L;
        Long locationId = 13L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(locationId)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, userId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(35L);
        graph.setName("Resolution Percent");
        graph.setData(Map.of(
            "type", "indicator",
            "name", "Trace 1",
            "mode", "gauge+number",
            "value", 68,
            "number", Map.of(
                "suffix", "%",
                "font", Map.of("size", 22)
            ),
            "gauge", Map.of(
                "shape", "angular",
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", "#1f77b4"),
                "borderwidth", 0,
                "steps", List.of(
                    Map.of("color", "#80000030", "range", List.of(0, 30)),
                    Map.of("color", "#FF000030", "range", List.of(30, 60)),
                    Map.of("color", "#FFFF0030", "range", List.of(60, 90)),
                    Map.of("color", "#00800030", "range", List.of(90, 100))
                )
            )
        ));
        graph.setLayout(Map.of(
            "margin", Map.of("b", 10, "l", 10, "r", 10, "t", 10),
            "showlegend", false
        ));
        graph.setConfig(Map.of("displayModeBar", false, "responsive", false));
        graph.setStyle(Map.of("height", 160));
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(35))
            .andExpect(jsonPath("$[0].name").value("Resolution Percent"))
            .andExpect(jsonPath("$[0].data[0].type").value("indicator"))
            .andExpect(jsonPath("$[0].data[0].value").value(68))
            .andExpect(jsonPath("$[0].data[0].number.suffix").value("%"))
            .andExpect(jsonPath("$[0].data[0].gauge.axis.range[0]").value(0))
            .andExpect(jsonPath("$[0].data[0].gauge.axis.range[1]").value(100))
            .andExpect(jsonPath("$[0].data[0].gauge.bar.color").value("#1f77b4"))
            .andExpect(jsonPath("$[0].data[0].gauge.steps[0].color").value("#80000030"))
            .andExpect(jsonPath("$[0].data[0].gauge.steps[3].range[1]").value(100))
            .andExpect(jsonPath("$[0].layout.showlegend").value(false))
            .andExpect(jsonPath("$[0].config.displayModeBar").value(false))
            .andExpect(jsonPath("$[0].config.responsive").value(false))
            .andExpect(jsonPath("$[0].style.height").value(160));

        verify(locationRepository).existsById(locationId);
        verify(locationUserRepository).existsByIdLocationIdAndIdUserId(locationId, userId);
        verify(locationGraphRepository).findByLocationIdWithGraph(locationId);
    }

    @Test
    void locationGraphsIgnoresLegacyTemplateSnapshotWhenRelationalPayloadExists() throws Exception {
        Long userId = 18L;
        Long locationId = 44L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(locationId)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, userId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(47L);
        graph.setName("Relational payload");
        graph.setLayout(Map.of("showlegend", true));
        graph.setConfig(Map.of("responsive", true));
        graph.setStyle(Map.of("width", "100%"));
        graph.setData(List.of(Map.of("type", "bar", "name", "Sessions", "y", List.of(4, 9, 6))));
        setRawGraphData(graph, List.of(Map.of("type", "pie", "labels", List.of("legacy"), "values", List.of(1))));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(47))
            .andExpect(jsonPath("$[0].data[0].type").value("bar"))
            .andExpect(jsonPath("$[0].data[0].name").value("Sessions"))
            .andExpect(jsonPath("$[0].data[0].y[0]").value(4))
            .andExpect(jsonPath("$[0].layout.showlegend").value(true))
            .andExpect(jsonPath("$[0].config.responsive").value(true))
            .andExpect(jsonPath("$[0].style.width").value("100%"));
    }

    @Test
    void locationGraphsReturnsEmptyDataWhenOnlyLegacyTemplateSnapshotExists() throws Exception {
        Long userId = 25L;
        Long locationId = 66L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(locationId)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, userId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(101L);
        graph.setName("Legacy-only graph");
        graph.setLayout(Map.of("showlegend", false));
        graph.setConfig(Map.of("responsive", false));
        graph.setStyle(Map.of("height", 200));
        setRawGraphData(graph, List.of(Map.of("type", "bar"), "bad-entry"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(101))
            .andExpect(jsonPath("$[0].data").isArray())
            .andExpect(jsonPath("$[0].data").isEmpty())
            .andExpect(jsonPath("$[0].layout.showlegend").value(false))
            .andExpect(jsonPath("$[0].config.responsive").value(false))
            .andExpect(jsonPath("$[0].style.height").value(200));
    }

    @Test
    void updateLocationGraphDataWritesTraceDataAndLayoutThroughPipeline() throws Exception {
        Long userId = 41L;
        Long locationId = 77L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(310L);
        graph.setName("Editable graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setLayout(Map.of("title", "Original title"));
        graph.setConfig(Map.of("displayModeBar", false));
        graph.setStyle(Map.of("height", 240));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(locationId), anyCollection()))
            .thenReturn(List.of(graph));

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": 310,
                              "data": [
                                {"type": "bar", "y": [9, 8, 7]}
                              ],
                              "layout": {"title": "Updated by backend"}
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isNoContent());

        verify(graphRepository).saveAll(List.of(graph));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals(List.of(9L, 8L, 7L), traces.getFirst().get("y"));
        assertEquals(Map.of("title", "Updated by backend"), graph.getLayout());
    }

    @Test
    void updateLocationGraphDataPersistsRenamedAndNewTracesThroughPipeline() throws Exception {
        Long userId = 43L;
        Long locationId = 79L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(311L);
        graph.setName("Editable graph");
        graph.setData(List.of(Map.of("type", "bar", "name", "Current", "y", List.of(1, 2, 3))));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(locationId), anyCollection()))
            .thenReturn(List.of(graph));

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": 311,
                              "data": [
                                {"type": "bar", "name": "Actual", "x": ["Jan"], "y": [9]},
                                {"type": "bar", "name": "Forecast", "x": ["Feb"], "y": [12]}
                              ]
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isNoContent());

        verify(graphRepository).saveAll(List.of(graph));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(2, traces.size());
        assertEquals("Actual", traces.get(0).get("name"));
        assertEquals(List.of(9L), traces.get(0).get("y"));
        assertEquals("Forecast", traces.get(1).get("name"));
        assertEquals(List.of(12L), traces.get(1).get("y"));
    }

    @Test
    void createLocationGraphBuildsDefaultPayloadAndAppendsItToRequestedSection() throws Exception {
        Long userId = 47L;
        Long locationId = 83L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        var location = new com.aphinity.client_analytics_core.api.core.entities.location.Location();
        location.setId(locationId);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of(
            "sections",
            List.of(
                Map.of("section_id", 1, "graph_ids", List.of(300L)),
                Map.of("section_id", 2, "graph_ids", List.of())
            )
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(510L);
            graph.setCreatedAt(Instant.parse("2026-01-05T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-05T00:00:00Z"));
            return graph;
        });

        mockMvc.perform(
                post("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "sectionId": 2,
                          "graphType": "bar"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(510))
            .andExpect(jsonPath("$.name").value("New Bar Graph"))
            .andExpect(jsonPath("$.data[0].type").value("bar"))
            .andExpect(jsonPath("$.data[0].name").value("Trace 1"))
            .andExpect(jsonPath("$.data[0].marker.color").value("#1f77b4"))
            .andExpect(jsonPath("$.data[0].x").isArray())
            .andExpect(jsonPath("$.data[0].x").isEmpty())
            .andExpect(jsonPath("$.data[0].y").isArray())
            .andExpect(jsonPath("$.data[0].y").isEmpty())
            .andExpect(jsonPath("$.layout.title.text").value("Phoenix"))
            .andExpect(jsonPath("$.config.displayModeBar").value(false))
            .andExpect(jsonPath("$.config.responsive").value(false))
            .andExpect(jsonPath("$.style.height").value(320));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(300L), sections.get(0).get("graph_ids"));
        assertEquals(List.of(510L), sections.get(1).get("graph_ids"));
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void createLocationGraphBuildsIndicatorTemplateWithAGaugeTrace() throws Exception {
        Long userId = 53L;
        Long locationId = 87L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        var location = new com.aphinity.client_analytics_core.api.core.entities.location.Location();
        location.setId(locationId);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of(
            "sections",
            List.of(Map.of("section_id", 1, "graph_ids", List.of()))
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(513L);
            graph.setCreatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            return graph;
        });

        mockMvc.perform(
                post("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "sectionId": 1,
                          "graphType": "indicator"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(513))
            .andExpect(jsonPath("$.name").value("New Indicator Graph"))
            .andExpect(jsonPath("$.data[0].type").value("indicator"))
            .andExpect(jsonPath("$.data[0].name").value("Trace 1"))
            .andExpect(jsonPath("$.data[0].mode").value("gauge+number"))
            .andExpect(jsonPath("$.data[0].value").value(0))
            .andExpect(jsonPath("$.data[0].number.suffix").value("%"))
            .andExpect(jsonPath("$.data[0].number.font.size").value(22))
            .andExpect(jsonPath("$.data[0].gauge.shape").value("angular"))
            .andExpect(jsonPath("$.data[0].gauge.axis.range[0]").value(0))
            .andExpect(jsonPath("$.data[0].gauge.axis.range[1]").value(100))
            .andExpect(jsonPath("$.data[0].gauge.bar.color").value("#1f77b4"))
            .andExpect(jsonPath("$.data[0].gauge.steps[0].color").value("#80000030"))
            .andExpect(jsonPath("$.data[0].gauge.steps[3].range[1]").value(100))
            .andExpect(jsonPath("$.layout.margin.t").value(10))
            .andExpect(jsonPath("$.layout.showlegend").value(false))
            .andExpect(jsonPath("$.config.displayModeBar").value(false))
            .andExpect(jsonPath("$.config.responsive").value(false))
            .andExpect(jsonPath("$.style.height").value(160));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(513L), sections.getFirst().get("graph_ids"));
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void createLocationGraphBuildsScatterTemplateWithAScatterTrace() throws Exception {
        Long userId = 52L;
        Long locationId = 86L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        var location = new com.aphinity.client_analytics_core.api.core.entities.location.Location();
        location.setId(locationId);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of("sections", List.of(Map.of("section_id", 1, "graph_ids", List.of()))));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(512L);
            graph.setCreatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            return graph;
        });

        mockMvc.perform(
                post("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "sectionId": 1,
                          "graphType": "scatter"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(512))
            .andExpect(jsonPath("$.name").value("New Plot Graph"))
            .andExpect(jsonPath("$.data[0].type").value("scatter"))
            .andExpect(jsonPath("$.data[0].name").value("Trace 1"))
            .andExpect(jsonPath("$.data[0].x").isArray())
            .andExpect(jsonPath("$.data[0].x").isEmpty())
            .andExpect(jsonPath("$.data[0].y").isArray())
            .andExpect(jsonPath("$.data[0].y").isEmpty())
            .andExpect(jsonPath("$.data[0].line.color").value("#1f77b4"))
            .andExpect(jsonPath("$.data[0].line.width").value(2))
            .andExpect(jsonPath("$.data[0].mode").value("lines+markers"))
            .andExpect(jsonPath("$.data[0].marker.size").value(6))
            .andExpect(jsonPath("$.layout.yaxis.title").value("% Compliance"))
            .andExpect(jsonPath("$.layout.title.text").value("Phoenix"))
            .andExpect(jsonPath("$.config.displayModeBar").value(false))
            .andExpect(jsonPath("$.config.responsive").value(false));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(512L), sections.getFirst().get("graph_ids"));
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void createLocationGraphCreatesANewSectionWhenRequested() throws Exception {
        Long userId = 48L;
        Long locationId = 84L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        var location = new com.aphinity.client_analytics_core.api.core.entities.location.Location();
        location.setId(locationId);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of("sections", List.of()));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(graphRepository.saveAndFlush(any(Graph.class))).thenAnswer(invocation -> {
            Graph graph = invocation.getArgument(0);
            graph.setId(511L);
            graph.setCreatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            graph.setUpdatedAt(Instant.parse("2026-01-06T00:00:00Z"));
            return graph;
        });

        mockMvc.perform(
                post("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "createNewSection": true,
                          "graphType": "bar"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(511))
            .andExpect(jsonPath("$.name").value("New Bar Graph"))
            .andExpect(jsonPath("$.data[0].type").value("bar"))
            .andExpect(jsonPath("$.data[0].marker.color").value("#1f77b4"))
            .andExpect(jsonPath("$.data[0].x").isArray())
            .andExpect(jsonPath("$.data[0].x").isEmpty())
            .andExpect(jsonPath("$.data[0].y").isArray())
            .andExpect(jsonPath("$.data[0].y").isEmpty())
            .andExpect(jsonPath("$.config.displayModeBar").value(false))
            .andExpect(jsonPath("$.config.responsive").value(false))
            .andExpect(jsonPath("$.layout.title.text").value("Phoenix"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(1, sections.size());
        assertEquals(1L, ((Number) sections.getFirst().get("section_id")).longValue());
        assertEquals(List.of(511L), sections.getFirst().get("graph_ids"));
        verify(locationGraphRepository).save(any(LocationGraph.class));
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void deleteLocationGraphRemovesItFromSectionLayoutThroughPipeline() throws Exception {
        Long userId = 49L;
        Long locationId = 85L;
        Long graphId = 512L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);

        var location = new com.aphinity.client_analytics_core.api.core.entities.location.Location();
        location.setId(locationId);
        location.setName("Phoenix");
        location.setSectionLayout(Map.of(
            "sections",
            List.of(
                Map.of("section_id", 1, "graph_ids", List.of(graphId, 300L)),
                Map.of("section_id", 2, "graph_ids", List.of(301L))
            )
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));

        Graph graph = new Graph();
        graph.setId(graphId);
        graph.setName("Delete me");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(locationId, graphId))
            .thenReturn(Optional.of(graph));
        when(locationGraphRepository.findByIdGraphId(graphId)).thenReturn(List.of());

        mockMvc.perform(
                delete("/core/locations/{locationId}/graphs/{graphId}", locationId, graphId)
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) location.getSectionLayout().get("sections");
        assertEquals(List.of(300L), sections.get(0).get("graph_ids"));
        assertEquals(List.of(301L), sections.get(1).get("graph_ids"));
        verify(locationGraphRepository).deleteById(new com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId(locationId, graphId));
        verify(graphRepository).delete(graph);
        verify(locationRepository).saveAndFlush(location);
    }

    @Test
    void updateLocationGraphNameWritesNameThroughDedicatedEndpoint() throws Exception {
        Long userId = 44L;
        Long locationId = 80L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(410L);
        graph.setName("Editable graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setUpdatedAt(Instant.parse("2026-01-03T00:00:00Z"));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(locationId, 410L))
            .thenReturn(Optional.of(graph));
        when(graphRepository.saveAndFlush(graph)).thenReturn(graph);

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs/{graphId}/name", locationId, 410L)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {"name": "Renamed graph"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graphId").value(410))
            .andExpect(jsonPath("$.name").value("Renamed graph"))
            .andExpect(jsonPath("$.updatedAt").value("2026-01-03T00:00:00Z"));

        assertEquals("Renamed graph", graph.getName());
        verify(graphRepository).saveAndFlush(graph);
    }

    @Test
    void updateLocationGraphNameRejectsBlankGraphName() throws Exception {
        Long userId = 46L;
        Long locationId = 82L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(411L);
        graph.setName("Editable graph");
        graph.setData(List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        when(graphRepository.findByLocationIdAndGraphIdForUpdate(locationId, 411L))
            .thenReturn(Optional.of(graph));

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs/{graphId}/name", locationId, 411L)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {"name": "   "}
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_name_required"))
            .andExpect(jsonPath("$.message").value("Graph name is required"));
    }

    @Test
    void updateLocationGraphDataReturnsConflictWhenExpectedUpdatedAtIsStale() throws Exception {
        Long userId = 45L;
        Long locationId = 81L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(312L);
        graph.setName("Editable graph");
        graph.setData(List.of(Map.of("type", "bar", "name", "Current", "y", List.of(1, 2, 3))));
        graph.setUpdatedAt(Instant.parse("2026-01-10T00:00:00Z"));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(locationId), anyCollection()))
            .thenReturn(List.of(graph));

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": 312,
                              "expectedUpdatedAt": "2026-01-09T00:00:00Z",
                              "data": [
                                {"type": "bar", "name": "Actual", "x": ["Jan"], "y": [9]}
                              ]
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("graph_update_conflict"))
            .andExpect(jsonPath("$.message").value("Graph update conflict"));
    }

    @Test
    void updateLocationGraphDataReturnsBadRequestForInvalidIndicatorPayload() throws Exception {
        Long userId = 50L;
        Long locationId = 86L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        Graph graph = new Graph();
        graph.setId(313L);
        graph.setName("Resolution Percent");
        graph.setData(List.of(Map.of(
            "type", "indicator",
            "mode", "gauge+number",
            "value", 68,
            "number", Map.of(
                "suffix", "%",
                "font", Map.of("size", 22)
            ),
            "gauge", Map.of(
                "shape", "angular",
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", "#1f77b4"),
                "borderwidth", 0,
                "steps", List.of(
                    Map.of("color", "#80000030", "range", List.of(0, 30)),
                    Map.of("color", "#FF000030", "range", List.of(30, 60)),
                    Map.of("color", "#FFFF0030", "range", List.of(60, 90)),
                    Map.of("color", "#00800030", "range", List.of(90, 100))
                )
            )
        )));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(locationId), anyCollection()))
            .thenReturn(List.of(graph));

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": 313,
                              "data": [
                                {
                                  "type": "indicator",
                                  "mode": "gauge+number",
                                  "value": 150,
                                  "number": {
                                    "suffix": "%",
                                    "font": {"size": 22}
                                  },
                                  "gauge": {
                                    "shape": "angular",
                                    "axis": {"range": [0, 100]},
                                    "bar": {"color": "#1f77b4"},
                                    "borderwidth": 0,
                                    "steps": [
                                      {"color": "#80000030", "range": [0, 30]},
                                      {"color": "#FF000030", "range": [30, 60]},
                                      {"color": "#FFFF0030", "range": [60, 90]},
                                      {"color": "#00800030", "range": [90, 100]}
                                    ]
                                  }
                                }
                              ]
                            }
                          ]
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_data_invalid"))
            .andExpect(jsonPath("$.message").value("Graph data is invalid"));
    }

    @Test
    void updateLocationGraphDataReturnsBadRequestForDuplicateGraphIds() throws Exception {
        Long userId = 42L;
        Long locationId = 78L;

        AppUser user = verifiedUser(userId);
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.existsById(locationId)).thenReturn(true);

        mockMvc.perform(
                put("/core/locations/{locationId}/graphs", locationId)
                    .with(csrf().asHeader())
                    .contentType("application/json")
                    .content("""
                        {
                          "graphs": [
                            {"graphId": 310, "data": [{"type": "bar"}]},
                            {"graphId": 310, "data": [{"type": "scatter"}]}
                          ]
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_update_duplicates"))
            .andExpect(jsonPath("$.message").value("Graph update list contains duplicate graph ids"));

        verifyNoInteractions(graphRepository);
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
            var field = Graph.class.getDeclaredField("templateData");
            field.setAccessible(true);
            field.set(graph, rawData);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to set raw graph data for legacy payload test", ex);
        }
    }

    @TestConfiguration
    static class JwtArgumentResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType().equals(Jwt.class);
                }

                @Override
                public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
                ) {
                    return Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .subject("test-user")
                        .build();
                }
            });
        }
    }
}
