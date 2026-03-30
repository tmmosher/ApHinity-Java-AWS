package com.aphinity.client_analytics_core.api.core.services.dashboard;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AdminManagedUserPageResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.AdminManagedUserResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.UserDeletionService;
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
 * Admin-only user management operations.
 */
@Service
public class AdminUserManagementService {
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 12;

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleService accountRoleService;
    private final UserDeletionService userDeletionService;

    public AdminUserManagementService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        RoleRepository roleRepository,
        AccountRoleService accountRoleService,
        UserDeletionService userDeletionService
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.roleRepository = roleRepository;
        this.accountRoleService = accountRoleService;
        this.userDeletionService = userDeletionService;
    }

    /**
     * Returns a single page of users for admin user management.
     *
     * @param authenticatedUserId authenticated user id
     * @param page zero-based page index
     * @param size requested page size
     * @param query optional email search query
     * @return paginated user payload
     */
    @Transactional(readOnly = true)
    public AdminManagedUserPageResponse getUsers(Long authenticatedUserId, int page, int size, String query) {
        AppUser actor = appUserRepository.findById(authenticatedUserId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireAdmin(actor);

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String normalizedQuery = normalizeSearchQuery(query);
        Page<Long> userIds = normalizedQuery == null
            ? appUserRepository.findManagedUserIds(PageRequest.of(normalizedPage, normalizedSize))
            : appUserRepository.searchManagedUserIdsByEmail(normalizedQuery, PageRequest.of(normalizedPage, normalizedSize));
        List<Long> ids = userIds.getContent();
        Set<Long> queuedUserIds = userDeletionService.findQueuedUserIds(ids);

        List<AdminManagedUserResponse> users = List.of();
        if (!ids.isEmpty()) {
            Map<Long, AppUser> usersById = new LinkedHashMap<>();
            for (AppUser user : appUserRepository.findByIdIn(ids)) {
                usersById.put(user.getId(), user);
            }
            users = ids.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(user -> toManagedUserResponse(user, queuedUserIds.contains(user.getId())))
                .toList();
        }

        return new AdminManagedUserPageResponse(
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
    public AdminManagedUserResponse updateUserRole(Long authenticatedUserId, Long targetUserId, String roleName) {
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
        if (accountRoleService.resolveAccountRole(savedUser) == AccountRole.ADMIN) {
            userDeletionService.restoreUser(savedUser.getId());
        }
        return toManagedUserResponse(
            savedUser,
            userDeletionService.findQueuedUserIds(List.of(savedUser.getId())).contains(savedUser.getId())
        );
    }

    /**
     * Queues a user for scheduled deletion.
     */
    @Transactional(readOnly = true)
    public AdminManagedUserResponse markUserForDeletion(Long authenticatedUserId, Long targetUserId) {
        AppUser actor = appUserRepository.findById(authenticatedUserId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireAdmin(actor);

        AppUser targetUser = appUserRepository.findById(targetUserId)
            .orElseThrow(this::targetUserNotFound);
        AccountRole targetRole = accountRoleService.resolveAccountRole(targetUser);

        if (authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot mark your own account for deletion");
        }
        if (targetRole == AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin accounts cannot be deleted");
        }

        userDeletionService.queueUser(targetUser, targetRole);
        return toManagedUserResponse(targetUser, true);
    }

    /**
     * Restores a user from the scheduled deletion queue.
     */
    @Transactional(readOnly = true)
    public AdminManagedUserResponse restoreUserDeletion(Long authenticatedUserId, Long targetUserId) {
        AppUser actor = appUserRepository.findById(authenticatedUserId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireAdmin(actor);

        AppUser targetUser = appUserRepository.findById(targetUserId)
            .orElseThrow(this::targetUserNotFound);
        AccountRole targetRole = accountRoleService.resolveAccountRole(targetUser);
        if (targetRole == AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin accounts cannot be deleted");
        }

        userDeletionService.restoreUser(targetUserId);
        return toManagedUserResponse(targetUser, false);
    }

    private AdminManagedUserResponse toManagedUserResponse(AppUser user, boolean pendingDeletion) {
        return new AdminManagedUserResponse(
            user.getId(),
            normalizeName(user.getName()),
            user.getEmail(),
            accountRoleService.resolveAccountRole(user),
            pendingDeletion
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

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.strip();
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
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
