package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.ProfileResponse;
import com.aphinity.client_analytics_core.api.core.services.ProfileService;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.security.PasswordPolicyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountRoleService accountRoleService;

    @Spy
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getProfileReturnsMappedProfile() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(role("partner")));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.PARTNER);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));

        ProfileResponse response = profileService.getProfile(7L);

        assertEquals("Jane Doe", response.name());
        assertEquals("jane@example.com", response.email());
        assertEquals(true, response.verified());
        assertEquals(AccountRole.PARTNER, response.role());
    }

    @Test
    void getProfileReturnsEmptyNameWhenNameIsNull() {
        AppUser user = new AppUser();
        user.setId(8L);
        user.setEmail("client@example.com");
        user.setRoles(Set.of(role("client")));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.CLIENT);
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(user));

        ProfileResponse response = profileService.getProfile(8L);

        assertEquals("", response.name());
        assertEquals("client@example.com", response.email());
        assertEquals(false, response.verified());
        assertEquals(AccountRole.CLIENT, response.role());
    }

    @Test
    void getProfileRejectsUnknownUser() {
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.getProfile(999L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void updateProfileUpdatesNameAndEmail() {
        AppUser user = new AppUser();
        user.setId(3L);
        user.setName("Original");
        user.setEmail("original@example.com");
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(role("client")));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.CLIENT);

        when(appUserRepository.findById(3L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByEmail("updated@example.com")).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(user)).thenReturn(user);

        ProfileResponse response = profileService.updateProfile(3L, " Updated Name ", "UPdated@example.com");

        assertEquals("Updated Name", response.name());
        assertEquals("updated@example.com", response.email());
        assertEquals(false, response.verified());
        assertEquals(AccountRole.CLIENT, response.role());
        assertEquals("Updated Name", user.getName());
        assertEquals("updated@example.com", user.getEmail());
        assertEquals(null, user.getEmailVerifiedAt());
        verify(authService).issueAndSendVerificationCode(3L, "updated@example.com");
    }

    @Test
    void updateProfileRejectsDuplicateEmail() {
        AppUser user = new AppUser();
        user.setId(4L);
        user.setEmail("owner@example.com");
        user.setEmailVerifiedAt(Instant.now());

        AppUser existing = new AppUser();
        existing.setId(99L);
        existing.setEmail("taken@example.com");

        when(appUserRepository.findById(4L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updateProfile(4L, "Owner", "taken@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email already in use", ex.getReason());
    }

    @Test
    void updatePasswordRejectsIncorrectCurrentPassword() {
        AppUser user = new AppUser();
        user.setId(5L);
        user.setPasswordHash("encoded");
        user.setEmailVerifiedAt(Instant.now());

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-current", "encoded")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updatePassword(5L, "bad-current", "Newpass1!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Current password is incorrect", ex.getReason());
    }

    @Test
    void updatePasswordEncodesAndSavesNewPassword() {
        AppUser user = new AppUser();
        user.setId(6L);
        user.setPasswordHash("encoded");
        user.setEmailVerifiedAt(Instant.now());

        when(appUserRepository.findById(6L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-pass", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1!", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");

        profileService.updatePassword(6L, "current-pass", "NewPass1!");

        assertEquals("new-hash", user.getPasswordHash());
        verify(appUserRepository).save(user);
    }

    @Test
    void getProfileResolvesAdminRoleWithPrecedence() {
        AppUser user = new AppUser();
        user.setId(9L);
        user.setEmail("admin@example.com");
        Set<Role> roles = new HashSet<>();
        roles.add(role("client"));
        roles.add(role("partner"));
        roles.add(role("admin"));
        user.setRoles(roles);
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.ADMIN);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));

        ProfileResponse response = profileService.getProfile(9L);

        assertEquals(AccountRole.ADMIN, response.role());
    }

    @Test
    void updateProfileRejectsUnverifiedUser() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("unverified@example.com");
        user.setEmailVerifiedAt(null);

        when(appUserRepository.findById(10L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updateProfile(10L, "Name", "next@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
    }

    @Test
    void updateProfileRejectsPartnerEmailChange() {
        AppUser user = new AppUser();
        user.setId(12L);
        user.setName("Partner");
        user.setEmail("partner@example.com");
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(role("partner")));

        when(appUserRepository.findById(12L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.PARTNER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updateProfile(12L, "Partner", "partner-new@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Email changes are not allowed for this role", ex.getReason());
    }

    @Test
    void updateProfileRejectsAdminEmailChange() {
        AppUser user = new AppUser();
        user.setId(13L);
        user.setName("Admin");
        user.setEmail("admin@example.com");
        user.setEmailVerifiedAt(Instant.now());
        user.setRoles(Set.of(role("admin")));

        when(appUserRepository.findById(13L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.ADMIN);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updateProfile(13L, "Admin", "admin-new@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Email changes are not allowed for this role", ex.getReason());
    }

    @Test
    void updateProfileAllowsPartnerNameChangeWhenEmailIsUnchanged() {
        AppUser user = new AppUser();
        Instant verifiedAt = Instant.now();
        user.setId(14L);
        user.setName("Partner Original");
        user.setEmail("partner@example.com");
        user.setEmailVerifiedAt(verifiedAt);
        user.setRoles(Set.of(role("partner")));

        when(appUserRepository.findById(14L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.PARTNER);
        when(appUserRepository.saveAndFlush(user)).thenReturn(user);

        ProfileResponse response = profileService.updateProfile(14L, "Partner Updated", "partner@example.com");

        assertEquals("Partner Updated", response.name());
        assertEquals("partner@example.com", response.email());
        assertEquals(true, response.verified());
        assertEquals(verifiedAt, user.getEmailVerifiedAt());
        verify(authService, never()).issueAndSendVerificationCode(14L, "partner@example.com");
    }

    @Test
    void updateProfileKeepsVerificationWhenClientEmailIsUnchanged() {
        AppUser user = new AppUser();
        Instant verifiedAt = Instant.now();
        user.setId(15L);
        user.setName("Client Original");
        user.setEmail("client@example.com");
        user.setEmailVerifiedAt(verifiedAt);
        user.setRoles(Set.of(role("client")));

        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));
        when(accountRoleService.resolveAccountRole(user)).thenReturn(AccountRole.CLIENT);
        when(appUserRepository.saveAndFlush(user)).thenReturn(user);

        ProfileResponse response = profileService.updateProfile(15L, "Client Updated", "CLIENT@example.com");

        assertEquals("Client Updated", response.name());
        assertEquals("client@example.com", response.email());
        assertEquals(true, response.verified());
        assertEquals(verifiedAt, user.getEmailVerifiedAt());
        verify(authService, never()).issueAndSendVerificationCode(15L, "client@example.com");
    }

    @Test
    void updatePasswordRejectsUnverifiedUser() {
        AppUser user = new AppUser();
        user.setId(11L);
        user.setPasswordHash("encoded");
        user.setEmailVerifiedAt(null);

        when(appUserRepository.findById(11L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            profileService.updatePassword(11L, "current-pass", "NewPass1!")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
