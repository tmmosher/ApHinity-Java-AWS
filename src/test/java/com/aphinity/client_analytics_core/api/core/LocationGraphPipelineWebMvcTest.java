package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.security.AccessTokenRefreshFilter;
import com.aphinity.client_analytics_core.api.core.controllers.LocationController;
import com.aphinity.client_analytics_core.api.core.entities.Graph;
import com.aphinity.client_analytics_core.api.core.entities.LocationGraph;
import com.aphinity.client_analytics_core.api.core.repositories.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.LocationService;
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

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private LocationGraphRepository locationGraphRepository;

    @MockitoBean
    private LocationUserRepository locationUserRepository;

    @MockitoBean
    private AccountRoleService accountRoleService;

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
    void locationGraphsNormalizesLegacyNestedDatabaseShapeBeforeTransmission() throws Exception {
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
        graph.setName("Legacy payload");
        setRawGraphData(graph, Map.of(
            "data", List.of(Map.of("type", "bar", "name", "Sessions", "y", List.of(4, 9, 6))),
            "layout", Map.of("showlegend", true),
            "config", Map.of("responsive", true),
            "style", Map.of("width", "100%")
        ));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(47))
            .andExpect(jsonPath("$[0].data[0].type").value("bar"))
            .andExpect(jsonPath("$[0].data[0].name").value("Sessions"))
            .andExpect(jsonPath("$[0].layout.showlegend").value(true))
            .andExpect(jsonPath("$[0].config.responsive").value(true))
            .andExpect(jsonPath("$[0].style.width").value("100%"));
    }

    @Test
    void locationGraphsReturnsServerErrorWhenStoredGraphPayloadIsMalformed() throws Exception {
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
        graph.setName("Malformed graph");
        setRawGraphData(graph, List.of(Map.of("type", "bar"), "bad-entry"));

        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setGraph(graph);
        when(locationGraphRepository.findByLocationIdWithGraph(locationId)).thenReturn(List.of(locationGraph));

        mockMvc.perform(get("/core/locations/{locationId}/graphs", locationId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("request_failed"))
            .andExpect(jsonPath("$.message").value("Request failed"));
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
