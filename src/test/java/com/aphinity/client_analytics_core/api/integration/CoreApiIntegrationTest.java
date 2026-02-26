package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.core.entities.Graph;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass1!";

    @Test
    void locationsRejectMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/core/locations"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void locationsReturnsMembershipScopedDataForClientUser() throws Exception {
        AppUser user = createUser("client-locations@example.com", PASSWORD, true, "client");
        Location location = createLocation("Austin");
        addMembership(location, user);

        AuthCookies authCookies = loginAndCaptureCookies("client-locations@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Austin"));
    }

    @Test
    void locationsAutoRefreshesExpiredAccessTokenWhenRefreshTokenIsValid() throws Exception {
        AppUser user = createUser("refresh-core@example.com", PASSWORD, true, "client");
        Location location = createLocation("Denver");
        addMembership(location, user);

        AuthCookies loginCookies = loginAndCaptureCookies("refresh-core@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(user, initialSession.getId());

        MvcResult result = mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(expiredAccessToken, loginCookies.refreshToken()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Denver"))
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
        assertEquals(2L, authSessionRepository.count());
    }

    @Test
    void locationsClearsCookiesWhenExpiredAccessTokenHasInvalidRefreshToken() throws Exception {
        AppUser user = createUser("invalid-refresh@example.com", PASSWORD, true, "client");
        String expiredAccessToken = createExpiredAccessToken(user, 999L);

        MvcResult result = mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(expiredAccessToken, "bogus-refresh-token"))
            )
            .andExpect(status().isUnauthorized())
            .andReturn();

        Map<String, String> clearedCookies = readSetCookies(result);
        assertThat(clearedCookies).containsEntry(AuthCookieNames.ACCESS_COOKIE_NAME, "");
        assertThat(clearedCookies).containsEntry(AuthCookieNames.REFRESH_COOKIE_NAME, "");
    }

    @Test
    void concurrentExpiredAccessRequestsShareSingleRefreshExecution() throws Exception {
        int requestCount = 6;
        AppUser user = createUser("race-core@example.com", PASSWORD, true, "client");
        Location location = createLocation("Phoenix");
        addMembership(location, user);
        AuthCookies loginCookies = loginAndCaptureCookies("race-core@example.com", PASSWORD);

        AuthSession originalSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(user, originalSession.getId());

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(concurrentLocationRequestTask(
                    ready,
                    start,
                    expiredAccessToken,
                    loginCookies.refreshToken()
                )));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<MvcResult> future : futures) {
                MvcResult response = future.get(10, TimeUnit.SECONDS);
                assertEquals(HttpStatus.OK.value(), response.getResponse().getStatus());
                assertThat(response.getResponse().getContentAsString()).contains("Phoenix");
                Map<String, String> setCookies = readSetCookies(response);
                assertThat(setCookies).containsKey(AuthCookieNames.ACCESS_COOKIE_NAME);
                assertThat(setCookies).containsKey(AuthCookieNames.REFRESH_COOKIE_NAME);
            }
        } finally {
            executor.shutdownNow();
        }

        List<AuthSession> sessions = authSessionRepository.findAll();
        long activeSessions = sessions.stream().filter(session -> session.getRevokedAt() == null).count();
        assertEquals(2L, sessions.size());
        assertEquals(1L, activeSessions);
    }

    @Test
    void deleteMembershipRemovesTargetUserMembership() throws Exception {
        AppUser partner = createUser("partner@example.com", PASSWORD, true, "partner");
        AppUser target = createUser("target@example.com", PASSWORD, true, "client");
        Location location = createLocation("Chicago");
        addMembership(location, target);

        AuthCookies authCookies = loginAndCaptureCookies("partner@example.com", PASSWORD);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/memberships/{userId}", location.getId(), target.getId())
                    .cookie(authCookies(authCookies))
                    .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
            )
            .andExpect(status().isNoContent());

        assertTrue(locationUserRepository.findByIdLocationIdAndIdUserId(location.getId(), target.getId()).isEmpty());
    }

    @Test
    void updateLocationGraphsAllowsPartnerAndOnlyMutatesData() throws Exception {
        AppUser partner = createUser("partner-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Santa Ana");
        Graph graph = createGraph("Water quality", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setLayout(Map.of("title", "Original layout"));
        graphRepository.save(graph);
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrf().asHeader())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}],
                              "layout": {"title": "Ignored by backend"}
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(graph.getId()))
            .andExpect(jsonPath("$[0].data[0].y[0]").value(9))
            .andExpect(jsonPath("$[0].layout.title").value("Original layout"));
    }

    @Test
    void updateLocationGraphsRejectsClientUsers() throws Exception {
        createUser("client-graphs@example.com", PASSWORD, true, "client");
        Location location = createLocation("Irvine");
        Graph graph = createGraph("Client graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("client-graphs@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrf().asHeader())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void updateLocationGraphsRejectsGraphNotAssignedToLocation() throws Exception {
        createUser("partner-missing-graph@example.com", PASSWORD, true, "partner");
        Location targetLocation = createLocation("Anaheim");
        Location otherLocation = createLocation("Fullerton");
        Graph otherGraph = createGraph("Other location graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(otherLocation, otherGraph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-missing-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", targetLocation.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrf().asHeader())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(otherGraph.getId()))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("location_graph_not_found"));
    }

    @Test
    void updateLocationGraphsRejectsInvalidGraphDataPayload() throws Exception {
        createUser("partner-invalid-graph@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Tustin");
        Graph graph = createGraph("Invalid graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-invalid-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrf().asHeader())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar"}, "bad-entry"]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_data_invalid"));
    }

    @Test
    void updateLocationGraphsAutoRefreshesExpiredAccessTokenWhenRefreshTokenIsValid() throws Exception {
        AppUser partner = createUser("partner-refresh-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Costa Mesa");
        Graph graph = createGraph("Refresh graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies loginCookies = loginAndCaptureCookies("partner-refresh-graphs@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(partner, initialSession.getId());

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(expiredAccessToken, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [5, 5, 5]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
    }

    @Test
    void updateLocationGraphsSupportsConcurrentWritesWithoutServerErrors() throws Exception {
        int requestCount = 4;
        AppUser partner = createUser("partner-concurrent-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Laguna Beach");
        Graph graph = createGraph("Concurrent graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);
        AuthCookies authCookies = loginAndCaptureCookies("partner-concurrent-graphs@example.com", PASSWORD);

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                int value = i + 10;
                futures.add(executor.submit(concurrentGraphUpdateTask(
                    ready,
                    start,
                    authCookies,
                    location.getId(),
                    graph.getId(),
                    value
                )));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<MvcResult> future : futures) {
                MvcResult response = future.get(10, TimeUnit.SECONDS);
                assertEquals(HttpStatus.NO_CONTENT.value(), response.getResponse().getStatus());
            }
        } finally {
            executor.shutdownNow();
        }

        Graph persisted = graphRepository.findById(graph.getId()).orElseThrow();
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(persisted.getData());
        assertEquals(1, traces.size());
        assertThat(traces.getFirst()).containsEntry("type", "bar");
    }

    private Callable<MvcResult> concurrentLocationRequestTask(
        CountDownLatch ready,
        CountDownLatch start,
        String expiredAccessToken,
        String refreshToken
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(
                    get("/api/core/locations")
                        .cookie(
                            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, expiredAccessToken),
                            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, refreshToken)
                        )
                )
                .andReturn();
        };
    }

    private Callable<MvcResult> concurrentGraphUpdateTask(
        CountDownLatch ready,
        CountDownLatch start,
        AuthCookies authCookies,
        Long locationId,
        Long graphId,
        int nextValue
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(
                    put("/api/core/locations/{locationId}/graphs", locationId)
                        .cookie(authCookies(authCookies))
                        .with(csrf().asHeader())
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "graphs": [
                                {
                                  "graphId": %d,
                                  "data": [{"type":"bar","y":[%d,%d,%d]}]
                                }
                              ]
                            }
                            """.formatted(graphId, nextValue, nextValue, nextValue))
                )
                .andReturn();
        };
    }
}
