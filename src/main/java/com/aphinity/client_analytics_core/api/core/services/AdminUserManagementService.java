package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRolePageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRoleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Admin-only user role management operations.
 */
@Service
public class AdminUserManagementService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleService accountRoleService;

    public AdminUserManagementService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        RoleRepository roleRepository,
        AccountRoleService accountRoleService
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.roleRepository = roleRepository;
        this.accountRoleService = accountRoleService;
    }

    /**
     * Returns a single page of users for admin role management.
     *
     * @param authenticatedUserId authenticated user id
     * @param page zero-based page index
     * @param size requested page size
     * @return paginated user role payload
     */
    @Transactional(readOnly = true)
    public AdminUserRolePageResponse getUsers(Long authenticatedUserId, int page, int size) {
        AppUser actor = appUserRepository.findById(authenticatedUserId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireAdmin(actor);

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        Page<Long> userIds = appUserRepository.findPagedUserIds(PageRequest.of(normalizedPage, normalizedSize));
        List<Long> ids = userIds.getContent();

        List<AdminUserRoleResponse> users = List.of();
        if (!ids.isEmpty()) {
            Map<Long, AppUser> usersById = new LinkedHashMap<>();
            for (AppUser user : appUserRepository.findByIdIn(ids)) {
                usersById.put(user.getId(), user);
            }
            users = ids.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(this::toAdminUserRoleResponse)
                .toList();
        }

        return new AdminUserRolePageResponse(
            users,
            normalizedPage,
            normalizedSize,
            userIds.getTotalElements(),
            userIds.getTotalPages()
        );
    }

    /**
     * Updates a target user's account-level role.
     *
     * @param authenticatedUserId authenticated admin user id
     * @param targetUserId target user id
     * @param roleName target account role name
     * @return updated user payload
     */
    @Transactional
    public AdminUserRoleResponse updateUserRole(Long authenticatedUserId, Long targetUserId, String roleName) {
        AppUser actor = appUserRepository.findById(authenticatedUserId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireAdmin(actor);

        AppUser targetUser = appUserRepository.findById(targetUserId)
            .orElseThrow(this::targetUserNotFound);

        if (authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change your own role");
        }

        String normalizedRoleName = normalizeRoleName(roleName);
        Role targetRole = roleRepository.findByName(normalizedRoleName)
            .orElseThrow(this::invalidRole);

        targetUser.setRoles(new HashSet<>(Set.of(targetRole)));
        AppUser savedUser = appUserRepository.saveAndFlush(targetUser);
        authSessionRepository.revokeAllActiveForUser(targetUserId, Instant.now());
        return toAdminUserRoleResponse(savedUser);
    }

    private AdminUserRoleResponse toAdminUserRoleResponse(AppUser user) {
        return new AdminUserRoleResponse(
            user.getId(),
            normalizeName(user.getName()),
            user.getEmail(),
            accountRoleService.resolveAccountRole(user)
        );
    }

    private int normalizePage(int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index is invalid");
        }
        return page;
    }

    private int normalizePageSize(int size) {
        if (size == 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size is invalid");
        }
        return size;
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw invalidRole();
        }
        return roleName.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name;
    }

    private void requireAdmin(AppUser user) {
        if (accountRoleService.resolveAccountRole(user) != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private ResponseStatusException targetUserNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
    }

    private ResponseStatusException invalidRole() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is invalid");
    }
}
