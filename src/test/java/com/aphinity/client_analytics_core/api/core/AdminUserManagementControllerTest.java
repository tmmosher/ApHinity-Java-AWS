package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.AdminUserManagementController;
import com.aphinity.client_analytics_core.api.core.requests.AdminUserRoleUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRolePageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRoleResponse;
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
        AdminUserRolePageResponse expected = new AdminUserRolePageResponse(
            List.of(new AdminUserRoleResponse(3L, "Client", "client@example.com", AccountRole.CLIENT)),
            1,
            20,
            25,
            2
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.getUsers(7L, 1, 20)).thenReturn(expected);

        AdminUserRolePageResponse actual = adminUserManagementController.users(jwt, 1, 20);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).getUsers(7L, 1, 20);
    }

    @Test
    void updateUserRoleDelegatesToService() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest("partner");
        AdminUserRoleResponse expected = new AdminUserRoleResponse(11L, "Partner", "partner@example.com", AccountRole.PARTNER);

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(adminUserManagementService.updateUserRole(7L, 11L, "partner")).thenReturn(expected);

        AdminUserRoleResponse actual = adminUserManagementController.updateUserRole(jwt, 11L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(adminUserManagementService).updateUserRole(7L, 11L, "partner");
    }
}
