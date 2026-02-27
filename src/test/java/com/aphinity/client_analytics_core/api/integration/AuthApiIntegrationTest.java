package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass1!";

    @Test
    void signupCreatesUserWithClientRoleAndVerificationToken() throws Exception {
        String body = """
            {"email":"NewUser@Example.com","password":"ValidPass1!","name":"  jane doe  "}
            """;

        mockMvc.perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk())
            .andExpect(content().string("Account created successfully."));

        Optional<AppUser> createdUser = appUserRepository.findByEmail("newuser@example.com");
        assertThat(createdUser).isPresent();
        assertEquals("JANE DOE", createdUser.orElseThrow().getName());
        assertEquals(Set.of("client"), createdUser.orElseThrow().getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));

        Long tokenCount = jdbcTemplate.queryForObject(
            "select count(*) from email_verification_token where user_id = ?",
            Long.class,
            createdUser.orElseThrow().getId()
        );
        assertEquals(1L, tokenCount);
    }

    @Test
    void loginIssuesCookiesAndPersistsSession() throws Exception {
        createUser("client@example.com", PASSWORD, true, "client");

        MvcResult result = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"Client@Example.com","password":"ValidPass1!"}
                        """)
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> cookies = readSetCookies(result);
        assertNotNull(cookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(cookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
        assertEquals(1L, authSessionRepository.count());
    }

    @Test
    void loginRequiresCaptchaAfterThreeFailures() throws Exception {
        createUser("locked@example.com", PASSWORD, true, "client");
        String wrongPasswordPayload = """
            {"email":"locked@example.com","password":"WrongPass1!"}
            """;

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordPayload)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        }

        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"email":"locked@example.com","password":"ValidPass1!"}
                        """)
            )
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("captcha_required"));
    }

    @Test
    void refreshRejectsMissingRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("missing_refresh_token"));
    }

    @Test
    void refreshDoesNotRequireCsrfToken() throws Exception {
        createUser("csrf@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("csrf@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, authCookies.refreshToken()))
            )
            .andExpect(status().isNoContent());
    }

    @Test
    void refreshReplayWithinGraceKeepsRotatedSessionUsable() throws Exception {
        createUser("grace@example.com", PASSWORD, true, "client");
        AuthCookies loginCookies = loginAndCaptureCookies("grace@example.com", PASSWORD);

        MvcResult firstRefresh = mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent())
            .andReturn();

        String rotatedRefresh = readSetCookies(firstRefresh).get(AuthCookieNames.REFRESH_COOKIE_NAME);
        assertNotNull(rotatedRefresh);

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, rotatedRefresh))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent());
    }

    @Test
    void staleRefreshReplayRevokesAllActiveSessions() throws Exception {
        createUser("stale@example.com", PASSWORD, true, "client");
        AuthCookies loginCookies = loginAndCaptureCookies("stale@example.com", PASSWORD);

        MvcResult firstRefresh = mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent())
            .andReturn();

        String rotatedRefresh = readSetCookies(firstRefresh).get(AuthCookieNames.REFRESH_COOKIE_NAME);
        assertNotNull(rotatedRefresh);

        Thread.sleep(6200L);

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, rotatedRefresh))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));

        long activeSessions = authSessionRepository.findAll().stream()
            .filter(session -> session.getRevokedAt() == null)
            .count();
        assertEquals(0L, activeSessions);
    }

    @Test
    void logoutRevokesSessionAndClearsCookies() throws Exception {
        createUser("logout@example.com", PASSWORD, true, "client");
        AuthCookies loginCookies = loginAndCaptureCookies("logout@example.com", PASSWORD);

        MvcResult logoutResult = mockMvc.perform(
                post("/api/auth/logout")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> clearedCookies = readSetCookies(logoutResult);
        assertThat(clearedCookies).containsEntry(AuthCookieNames.ACCESS_COOKIE_NAME, "");
        assertThat(clearedCookies).containsEntry(AuthCookieNames.REFRESH_COOKIE_NAME, "");

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, loginCookies.refreshToken()))
                    .with(csrf().asHeader())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));
    }
}
