package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.AdminUserRoleUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserPageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserResponse;
import com.aphinity.client_analytics_core.api.core.services.AdminUserManagementService;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only user role management endpoints.
 */
@RestController
@RequestMapping({"/core", "/api/core"})
public class AdminUserManagementController {
    private final AdminUserManagementService adminUserManagementService;
    private final AuthenticatedUserService authenticatedUserService;

    public AdminUserManagementController(
        AdminUserManagementService adminUserManagementService,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.adminUserManagementService = adminUserManagementService;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Returns a paginated user list for admin user management.
     *
     * @param jwt authenticated principal JWT
     * @param page zero-based page index
     * @param size page size
     * @param query optional email search query
     * @return paginated users
     */
    @GetMapping("/admin/users")
    public AdminManagedUserPageResponse users(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(required = false) String query
    ) {
        return adminUserManagementService.getUsers(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            page,
            size,
            query
        );
    }

    /**
     * Updates the account role for a target user.
     *
     * @param jwt authenticated principal JWT
     * @param userId target user id
     * @param request validated update request
     * @return updated user payload
     */
    @PutMapping("/admin/users/{userId}/role")
    public AdminManagedUserResponse updateUserRole(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId,
        @Valid @RequestBody AdminUserRoleUpdateRequest request
    ) {
        return adminUserManagementService.updateUserRole(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            userId,
            request.role()
        );
    }

    /**
     * Queues a user for scheduled deletion.
     */
    @PutMapping("/admin/users/{userId}/deletion")
    public AdminManagedUserResponse markUserForDeletion(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId
    ) {
        return adminUserManagementService.markUserForDeletion(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            userId
        );
    }

    /**
     * Restores a user from the scheduled deletion queue.
     */
    @DeleteMapping("/admin/users/{userId}/deletion")
    public AdminManagedUserResponse restoreUserDeletion(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long userId
    ) {
        return adminUserManagementService.restoreUserDeletion(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            userId
        );
    }
}
