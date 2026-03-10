package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserManagementApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass1!";

    @Test
    void adminCanListUsersWithPagination() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        createUser("client1@example.com", PASSWORD, true, "client");
        createUser("client2@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/admin/users")
                    .param("page", "0")
                    .param("size", "2")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.users.length()").value(2));
    }

    @Test
    void adminCanUpdateAnotherUsersRole() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/role", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"role":"partner"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetUser.getId()))
            .andExpect(jsonPath("$.role").value("partner"));

        AppUser updatedUser = appUserRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(Set.of("partner"), updatedUser.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void updatingUserRoleRevokesTargetsRefreshSessions() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies adminCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);
        AuthCookies targetCookies = loginAndCaptureCookies("client@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/role", targetUser.getId())
                    .cookie(authCookies(adminCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"role":"partner"}
                        """)
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, targetCookies.refreshToken()))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));
    }

    @Test
    void partnerCannotListAdminUsers() throws Exception {
        createUser("partner@example.com", PASSWORD, true, "partner");
        AuthCookies authCookies = loginAndCaptureCookies("partner@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/admin/users")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
