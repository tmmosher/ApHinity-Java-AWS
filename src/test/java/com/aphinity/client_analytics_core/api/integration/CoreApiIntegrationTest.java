package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.core.entities.Location;
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
}
