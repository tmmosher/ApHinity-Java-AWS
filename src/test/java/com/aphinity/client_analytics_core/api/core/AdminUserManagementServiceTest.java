package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserPageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminManagedUserResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.AdminUserManagementService;
import com.aphinity.client_analytics_core.api.core.services.UserDeletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @Mock
    private UserDeletionService userDeletionService;

    @InjectMocks
    private AdminUserManagementService adminUserManagementService;

    @Test
    void getUsersReturnsPagedUsersForAdmin() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser partner = user(9L, "partner@example.com", "Partner", role("partner"));
        AppUser client = user(11L, "client@example.com", "Client", role("client"));
        Page<Long> page = new PageImpl<>(List.of(9L, 11L), PageRequest.of(0, 12), 2);

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findManagedUserIds(PageRequest.of(0, 12))).thenReturn(page);
        when(appUserRepository.findByIdIn(List.of(9L, 11L))).thenReturn(List.of(client, partner));
        when(accountRoleService.resolveAccountRole(partner)).thenReturn(AccountRole.PARTNER);
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);
        when(userDeletionService.findQueuedUserIds(List.of(9L, 11L))).thenReturn(Set.of(11L));

        AdminManagedUserPageResponse response = adminUserManagementService.getUsers(7L, 0, 12, null);

        assertEquals(2, response.users().size());
        assertEquals(9L, response.users().get(0).id());
        assertFalse(response.users().get(0).pendingDeletion());
        assertEquals(11L, response.users().get(1).id());
        assertTrue(response.users().get(1).pendingDeletion());
        assertEquals(2L, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void getUsersUsesEmailSearchWhenQueryPresent() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser client = user(11L, "client@example.com", "Client", role("client"));
        Page<Long> page = new PageImpl<>(List.of(11L), PageRequest.of(0, 12), 1);

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.searchManagedUserIdsByEmail("client", PageRequest.of(0, 12))).thenReturn(page);
        when(appUserRepository.findByIdIn(List.of(11L))).thenReturn(List.of(client));
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);
        when(userDeletionService.findQueuedUserIds(List.of(11L))).thenReturn(Set.of());

        AdminManagedUserPageResponse response = adminUserManagementService.getUsers(7L, 0, 12, " client ");

        assertEquals(1, response.users().size());
        assertEquals(11L, response.users().getFirst().id());
        verify(appUserRepository, never()).findManagedUserIds(any());
    }

    @Test
    void getUsersRejectsNonAdminActor() {
        AppUser partner = user(7L, "partner@example.com", "Partner", role("partner"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(partner));
        when(accountRoleService.resolveAccountRole(partner)).thenReturn(AccountRole.PARTNER);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.getUsers(7L, 0, 12, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
    }

    @Test
    void updateUserRoleReplacesRolesForTargetUser() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "client@example.com", "Client", role("client"));
        Role partnerRole = role("partner");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("partner")).thenReturn(Optional.of(partnerRole));
        when(appUserRepository.saveAndFlush(target)).thenReturn(target);
        when(accountRoleService.resolveAccountRole(target)).thenReturn(AccountRole.PARTNER);
        when(userDeletionService.findQueuedUserIds(List.of(11L))).thenReturn(Set.of());

        AdminManagedUserResponse response = adminUserManagementService.updateUserRole(7L, 11L, "partner");

        assertEquals(AccountRole.PARTNER, response.role());
        assertFalse(response.pendingDeletion());
        assertEquals(Set.of(partnerRole), target.getRoles());
        verify(appUserRepository).saveAndFlush(target);
        verify(authSessionRepository).revokeAllActiveForUser(eq(11L), any());
        verify(userDeletionService, never()).restoreUser(11L);
    }

    @Test
    void updateUserRoleClearsPendingDeletionWhenPromotedToAdmin() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "client@example.com", "Client", role("client"));
        Role adminRole = role("admin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("admin")).thenReturn(Optional.of(adminRole));
        when(appUserRepository.saveAndFlush(target)).thenReturn(target);
        when(accountRoleService.resolveAccountRole(target)).thenReturn(AccountRole.ADMIN);
        when(userDeletionService.findQueuedUserIds(List.of(11L))).thenReturn(Set.of());

        AdminManagedUserResponse response = adminUserManagementService.updateUserRole(7L, 11L, "admin");

        assertEquals(AccountRole.ADMIN, response.role());
        assertFalse(response.pendingDeletion());
        verify(userDeletionService).restoreUser(11L);
    }

    @Test
    void updateUserRoleRejectsSelfChange() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.updateUserRole(7L, 7L, "client")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You cannot change your own role", ex.getReason());
    }

    @Test
    void updateUserRoleRejectsUnknownRole() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "client@example.com", "Client", role("client"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("unknown")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.updateUserRole(7L, 11L, "unknown")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Role is invalid", ex.getReason());
    }

    @Test
    void markUserForDeletionQueuesNonAdminUsers() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "client@example.com", "Client", role("client"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(accountRoleService.resolveAccountRole(target)).thenReturn(AccountRole.CLIENT);

        AdminManagedUserResponse response = adminUserManagementService.markUserForDeletion(7L, 11L);

        assertTrue(response.pendingDeletion());
        verify(userDeletionService).queueUser(target, AccountRole.CLIENT);
    }

    @Test
    void markUserForDeletionRejectsSelfDeletion() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.markUserForDeletion(7L, 7L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You cannot mark your own account for deletion", ex.getReason());
    }

    @Test
    void markUserForDeletionRejectsAdminTarget() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "target-admin@example.com", "Target Admin", role("admin"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(accountRoleService.resolveAccountRole(target)).thenReturn(AccountRole.ADMIN);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.markUserForDeletion(7L, 11L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Admin accounts cannot be deleted", ex.getReason());
    }

    @Test
    void restoreUserDeletionClearsQueueState() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser target = user(11L, "client@example.com", "Client", role("client"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(11L)).thenReturn(Optional.of(target));
        when(accountRoleService.resolveAccountRole(target)).thenReturn(AccountRole.CLIENT);

        AdminManagedUserResponse response = adminUserManagementService.restoreUserDeletion(7L, 11L);

        assertFalse(response.pendingDeletion());
        verify(userDeletionService).restoreUser(11L);
    }

    private AppUser user(Long id, String email, String name, Role role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setRoles(Set.of(role));
        return user;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
