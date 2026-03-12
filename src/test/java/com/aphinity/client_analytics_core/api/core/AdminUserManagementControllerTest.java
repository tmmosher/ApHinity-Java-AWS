package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.AdminUserManagementController;
import com.aphinity.client_analytics_core.api.core.requests.AdminUserRoleUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserPageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserResponse;
import com.aphinity.client_analytics_core.api.core.services.AdminUserManagementService;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementControllerTest {
    @Mock
    private AdminUserManagementService adminUserManagementService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private AdminUserManagementController adminUserManagementController;

    @Test
    void usersDelegatesToService() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        AdminManagedUserPageResponse expected = new AdminManagedUserPageResponse(
            List.of(new AdminManagedUserResponse(3L, "Client", "client@example.com", AccountRole.CLIENT, false)),
            1,
            12,
            25,
            3
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.getUsers(7L, 1, 12, "client")).thenReturn(expected);

        AdminManagedUserPageResponse actual = adminUserManagementController.users(jwt, 1, 12, "client");

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).getUsers(7L, 1, 12, "client");
    }

    @Test
    void updateUserRoleDelegatesToService() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest("partner");
        AdminManagedUserResponse expected = new AdminManagedUserResponse(
            11L,
            "Partner",
            "partner@example.com",
            AccountRole.PARTNER,
            false
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.updateUserRole(7L, 11L, "partner")).thenReturn(expected);

        AdminManagedUserResponse actual = adminUserManagementController.updateUserRole(jwt, 11L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).updateUserRole(7L, 11L, "partner");
    }

    @Test
    void markUserForDeletionDelegatesToService() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        AdminManagedUserResponse expected = new AdminManagedUserResponse(
            11L,
            "Client",
            "client@example.com",
            AccountRole.CLIENT,
            true
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.markUserForDeletion(7L, 11L)).thenReturn(expected);

        AdminManagedUserResponse actual = adminUserManagementController.markUserForDeletion(jwt, 11L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).markUserForDeletion(7L, 11L);
    }

    @Test
    void restoreUserDeletionDelegatesToService() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        AdminManagedUserResponse expected = new AdminManagedUserResponse(
            11L,
            "Client",
            "client@example.com",
            AccountRole.CLIENT,
            false
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.restoreUserDeletion(7L, 11L)).thenReturn(expected);

        AdminManagedUserResponse actual = adminUserManagementController.restoreUserDeletion(jwt, 11L);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).restoreUserDeletion(7L, 11L);
    }
}
