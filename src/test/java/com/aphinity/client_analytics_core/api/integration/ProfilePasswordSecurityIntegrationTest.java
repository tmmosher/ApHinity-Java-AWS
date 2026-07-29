package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.core.services.dashboard.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfilePasswordSecurityIntegrationTest extends AbstractApiIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileService profileService;

    @Test
    void passwordChangeClearsCookiesAndRejectsPreviouslyIssuedAccessToken() throws Exception {
        String currentPassword = "CurrentPass12!";
        String newPassword = "ReplacementPass12!";
        createUser("password-change@example.com", currentPassword, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("password-change@example.com", currentPassword);

        MvcResult changeResult = mockMvc.perform(
                put("/api/core/profile/password")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(currentPassword, newPassword))
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> clearedCookies = readSetCookies(changeResult);
        assertThat(clearedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME)).isEmpty();
        assertThat(clearedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME)).isEmpty();
        assertThat(authSessionRepository.findAll())
            .allMatch(session -> session.getRevokedAt() != null);

        mockMvc.perform(
                get("/api/core/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authCookies.accessToken())
            )
            .andExpect(status().isUnauthorized());

        loginAndCaptureCookies("password-change@example.com", newPassword);
    }

    @Test
    void concurrentRefreshCannotLeaveAnActiveSessionAfterPasswordChange() throws Exception {
        String currentPassword = "ConcurrentPass12!";
        String newPassword = "ReplacementPass12!";
        var user = createUser("concurrent-password@example.com", currentPassword, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies(user.getEmail(), currentPassword);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Void> passwordChange = CompletableFuture.runAsync(() -> {
                await(start);
                profileService.updatePassword(user.getId(), currentPassword, newPassword);
            }, executor);
            CompletableFuture<Void> refresh = CompletableFuture.runAsync(() -> {
                await(start);
                try {
                    authService.refresh(authCookies.refreshToken(), "127.0.0.1", "concurrency-test");
                } catch (ResponseStatusException ex) {
                    // Password change may win the account lock and invalidate this refresh.
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }
            }, executor);

            start.countDown();
            CompletableFuture.allOf(passwordChange, refresh).get(10, TimeUnit.SECONDS);
        }

        assertThat(authSessionRepository.findAll())
            .allMatch(session -> session.getRevokedAt() != null);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting concurrent security operations", ex);
        }
    }
}
