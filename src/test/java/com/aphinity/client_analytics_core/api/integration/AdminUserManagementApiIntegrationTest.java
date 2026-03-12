package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.services.UserDeletionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserManagementApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass1!";

    @Autowired
    private UserDeletionService userDeletionService;

    @Test
    void adminCanListUsersWithPaginationSortedByNameAndExcludingAdmins() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser zeta = createUser("zeta@example.com", PASSWORD, true, "client");
        zeta.setName("Zeta User");
        appUserRepository.save(zeta);
        AppUser alpha = createUser("alpha@example.com", PASSWORD, true, "partner");
        alpha.setName("Alpha User");
        appUserRepository.save(alpha);
        createUser("another-admin@example.com", PASSWORD, true, "admin");

        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/admin/users")
                    .param("page", "0")
                    .param("size", "12")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(12))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.users.length()").value(2))
            .andExpect(jsonPath("$.users[0].email").value("alpha@example.com"))
            .andExpect(jsonPath("$.users[1].email").value("zeta@example.com"));
    }

    @Test
    void adminCanSearchUsersByEmailSubstringWithoutReturningAdmins() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        createUser("b-ops@example.com", PASSWORD, true, "client");
        createUser("a-ops@example.com", PASSWORD, true, "partner");
        createUser("admin-ops@example.com", PASSWORD, true, "admin");

        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/admin/users")
                    .param("page", "0")
                    .param("size", "12")
                    .param("query", "OPS")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.users.length()").value(2))
            .andExpect(jsonPath("$.users[0].email").value("a-ops@example.com"))
            .andExpect(jsonPath("$.users[1].email").value("b-ops@example.com"));
    }

    @Test
    void adminCanMarkAndRestoreUserDeletion() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/deletion", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetUser.getId()))
            .andExpect(jsonPath("$.pendingDeletion").value(true));

        mockMvc.perform(
                get("/api/core/admin/users")
                    .param("page", "0")
                    .param("size", "12")
                    .param("query", "client@")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users[0].pendingDeletion").value(true));

        mockMvc.perform(
                delete("/api/core/admin/users/{userId}/deletion", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingDeletion").value(false));
    }

    @Test
    void scheduledDeletionProcessorDeletesQueuedUsers() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/deletion", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk());

        userDeletionService.processPendingDeletions();

        assertTrue(appUserRepository.findById(targetUser.getId()).isEmpty());
    }

    @Test
    void scheduledDeletionProcessorRemovesTargetsRefreshSessions() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies adminCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);
        AuthCookies targetCookies = loginAndCaptureCookies("client@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/deletion", targetUser.getId())
                    .cookie(authCookies(adminCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk());

        userDeletionService.processPendingDeletions();

        assertTrue(appUserRepository.findById(targetUser.getId()).isEmpty());
        mockMvc.perform(
                post("/api/auth/refresh")
                    .cookie(new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, targetCookies.refreshToken()))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_refresh_token"));
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
            .andExpect(jsonPath("$.role").value("partner"))
            .andExpect(jsonPath("$.pendingDeletion").value(false));

        AppUser updatedUser = appUserRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(
            Set.of("partner"),
            updatedUser.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void promotingQueuedUserToAdminRemovesThemFromManagementResults() throws Exception {
        createUser("admin@example.com", PASSWORD, true, "admin");
        AppUser targetUser = createUser("client@example.com", PASSWORD, true, "client");
        AuthCookies authCookies = loginAndCaptureCookies("admin@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/deletion", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingDeletion").value(true));

        mockMvc.perform(
                put("/api/core/admin/users/{userId}/role", targetUser.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"role":"admin"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("admin"))
            .andExpect(jsonPath("$.pendingDeletion").value(false));

        mockMvc.perform(
                get("/api/core/admin/users")
                    .param("page", "0")
                    .param("size", "12")
                    .param("query", "client@")
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.users.length()").value(0));
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
    void partnerCannotListManagedUsers() throws Exception {
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
