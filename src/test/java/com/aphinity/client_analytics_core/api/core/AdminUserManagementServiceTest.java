package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRolePageResponse;
import com.aphinity.client_analytics_core.api.core.response.AdminUserRoleResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.AdminUserManagementService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private AdminUserManagementService adminUserManagementService;

    @Test
    void getUsersReturnsPagedUsersForAdmin() {
        AppUser admin = user(7L, "admin@example.com", "Admin", role("admin"));
        AppUser partner = user(9L, "partner@example.com", "Partner", role("partner"));
        AppUser client = user(11L, "client@example.com", "Client", role("client"));
        Page<Long> page = new PageImpl<>(List.of(9L, 11L), PageRequest.of(0, 20), 2);

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(accountRoleService.resolveAccountRole(admin)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findPagedUserIds(PageRequest.of(0, 20))).thenReturn(page);
        when(appUserRepository.findByIdIn(List.of(9L, 11L))).thenReturn(List.of(client, partner));
        when(accountRoleService.resolveAccountRole(partner)).thenReturn(AccountRole.PARTNER);
        when(accountRoleService.resolveAccountRole(client)).thenReturn(AccountRole.CLIENT);

        AdminUserRolePageResponse response = adminUserManagementService.getUsers(7L, 0, 20);

        assertEquals(2, response.users().size());
        assertEquals(9L, response.users().get(0).id());
        assertEquals(AccountRole.PARTNER, response.users().get(0).role());
        assertEquals(11L, response.users().get(1).id());
        assertEquals(AccountRole.CLIENT, response.users().get(1).role());
        assertEquals(2L, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void getUsersRejectsNonAdminActor() {
        AppUser partner = user(7L, "partner@example.com", "Partner", role("partner"));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(partner));
        when(accountRoleService.resolveAccountRole(partner)).thenReturn(AccountRole.PARTNER);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> adminUserManagementService.getUsers(7L, 0, 20)
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

        AdminUserRoleResponse response = adminUserManagementService.updateUserRole(7L, 11L, "partner");

        assertEquals(AccountRole.PARTNER, response.role());
        assertEquals(Set.of(partnerRole), target.getRoles());
        verify(appUserRepository).saveAndFlush(target);
        verify(authSessionRepository).revokeAllActiveForUser(org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.any());
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
