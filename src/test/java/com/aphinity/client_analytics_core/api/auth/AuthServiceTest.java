package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.auth.services.LoginAttemptService;
import com.aphinity.client_analytics_core.api.auth.services.TokenHasher;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import com.aphinity.client_analytics_core.api.security.JwtService;
import com.aphinity.client_analytics_core.api.security.PasswordPolicyValidator;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private TurnstileValidationService turnstileValidationService;

    @Mock
    private AsyncLogService asyncLogService;

    @Spy
    private PasswordPolicyValidator passwordPolicyValidator;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupRejectsExistingUser() {
        when(roleRepository.findByName("client")).thenReturn(Optional.of(clientRole(17L)));
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.signup("user@example.com", "Abcd123!","John", "10.0.0.1", "agent")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(appUserRepository).saveAndFlush(any(AppUser.class));
    }

    @Test
    void signupRejectsWeakPassword() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.signup("user@example.com", "short", "John","10.0.0.1", "agent")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(appUserRepository, never()).saveAndFlush(any(AppUser.class));
    }

    @Test
    void signupCreatesClientRoleForExternalEmail() {
        when(roleRepository.findByName("client")).thenReturn(Optional.of(clientRole(17L)));
        when(passwordEncoder.encode("Abcd123!")).thenReturn("hashed");
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup("user@example.com", "Abcd123!", "John","10.0.0.1", "agent");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(captor.capture());
        AppUser saved = captor.getValue();

        assertEquals("user@example.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals(1, saved.getRoles().size());
        Role role = saved.getRoles().iterator().next();
        assertEquals("client", role.getName());
        assertEquals(17L, role.getId());
    }

    @Test
    void signupCreatesOnlyClientRoleForAphinityEmail() {
        when(roleRepository.findByName("client")).thenReturn(Optional.of(clientRole(17L)));
        when(passwordEncoder.encode("Abcd123!")).thenReturn("hashed");
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup("user@aphinitytech.com", "Abcd123!","John" ,"10.0.0.1", "agent");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(captor.capture());
        AppUser saved = captor.getValue();

        assertEquals("user@aphinitytech.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals(Set.of("client"), saved.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(17L), saved.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet()));
        verify(roleRepository, never()).findByName("partner");
    }

    @Test
    void loginRejectsMissingUser() {
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.login("user@example.com", "Abcd123!", "10.0.0.1", "agent", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(loginAttemptService).recordFailure("user@example.com");
        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }

    @Test
    void loginRejectsMismatchedPassword() {
        AppUser user = new AppUser();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.login("user@example.com", "wrong", "10.0.0.1", "agent", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(loginAttemptService).recordFailure("user@example.com");
        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }

    @Test
    void loginReturnsTokensAndCreatesSession() {
        AppUser user = buildUser("user@example.com");

        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcd123!", "hashed")).thenReturn(true);
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(3600L);
        when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0);
            session.setId(99L);
            return session;
        });
        when(jwtService.createAccessToken(eq(user), anyLong())).thenReturn("access-token");

        Instant before = Instant.now();
        IssuedTokens tokens = authService.login("user@example.com", "Abcd123!", "10.0.0.1", "agent", null);
        Instant after = Instant.now();

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());
        AuthSession savedSession = sessionCaptor.getValue();

        assertEquals(user, savedSession.getUser());
        assertEquals("10.0.0.1", savedSession.getIpAddress());
        assertEquals("agent", savedSession.getUserAgent());
        assertNotNull(savedSession.getRefreshTokenHash());
        assertEquals(64, savedSession.getRefreshTokenHash().length());
        assertEquals(TokenHasher.sha256(tokens.refreshToken()), savedSession.getRefreshTokenHash());
        assertTrue(!savedSession.getExpiresAt().isBefore(before.plusSeconds(3600)));
        assertFalse(savedSession.getExpiresAt().isAfter(after.plusSeconds(3601)));

        assertEquals("access-token", tokens.accessToken());
        assertEquals("Bearer", tokens.tokenType());
        assertEquals(900L, tokens.expiresIn());
        assertEquals(3600L, tokens.refreshExpiresIn());
        assertNotNull(tokens.refreshToken());
        verify(jwtService).createAccessToken(user, 99L);
        verify(loginAttemptService).recordSuccess("user@example.com");
    }

    @Test
    void loginRequiresCaptchaWhenThresholdExceeded() {
        when(loginAttemptService.isCaptchaRequired("user@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.login("user@example.com", "Abcd123!", "10.0.0.1", "agent", null)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verify(turnstileValidationService, never()).validateTurnstileResponse(any(), any());
        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    void loginRejectsInvalidCaptcha() {
        when(loginAttemptService.isCaptchaRequired("user@example.com")).thenReturn(true);
        when(turnstileValidationService.validateTurnstileResponse("bad", "10.0.0.1")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.login("user@example.com", "Abcd123!", "10.0.0.1", "agent", "bad")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    void loginFailsWhenCaptchaServiceUnavailable() {
        when(loginAttemptService.isCaptchaRequired("user@example.com")).thenReturn(true);
        when(turnstileValidationService.validateTurnstileResponse("token", "10.0.0.1"))
            .thenThrow(new RuntimeException("no config"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.login("user@example.com", "Abcd123!", "10.0.0.1", "agent", "token")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    void refreshRejectsMissingSession() {
        String refreshToken = "refresh-token";
        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.refresh(refreshToken, "10.0.0.1", "agent")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void refreshRejectsRevokedSession() {
        String refreshToken = "refresh-token";
        AppUser user = buildUser("user@example.com");
        AuthSession session = buildSession(user, Instant.now().plusSeconds(60));
        session.setRevokedAt(Instant.now());

        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.refresh(refreshToken, "10.0.0.1", "agent")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(authSessionRepository).revokeAllActiveForUser(eq(user.getId()), any(Instant.class));
        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }

    @Test
    void refreshRejectsExpiredSession() {
        String refreshToken = "refresh-token";
        AppUser user = buildUser("user@example.com");
        AuthSession session = buildSession(user, Instant.now().minusSeconds(5));

        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            authService.refresh(refreshToken, "10.0.0.1", "agent")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(captor.capture());
        assertNotNull(captor.getValue().getRevokedAt());
    }

    @Test
    void refreshRotatesSessionAndTokens() {
        String refreshToken = "refresh-token";
        AppUser user = buildUser("user@example.com");
        AuthSession session = buildSession(user, Instant.now().plusSeconds(600));

        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.of(session));
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(3600L);
        when(authSessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(200L);
            }
            return saved;
        });
        when(jwtService.createAccessToken(user, 200L)).thenReturn("access-token");

        IssuedTokens tokens = authService.refresh(refreshToken, "10.0.0.1", "agent");

        assertEquals("access-token", tokens.accessToken());
        assertEquals("Bearer", tokens.tokenType());
        assertEquals(900L, tokens.expiresIn());
        assertEquals(3600L, tokens.refreshExpiresIn());
        assertNotNull(tokens.refreshToken());
        assertNotNull(session.getRevokedAt());
        assertEquals(200L, session.getReplacedBySessionId());
        verify(authSessionRepository).save(session);
    }

    @Test
    void logoutRevokesActiveSession() {
        String refreshToken = "refresh-token";
        AppUser user = buildUser("user@example.com");
        AuthSession session = buildSession(user, Instant.now().plusSeconds(600));

        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.of(session));

        authService.logout(refreshToken);

        assertNotNull(session.getRevokedAt());
        verify(authSessionRepository).save(session);
    }

    @Test
    void logoutIgnoresMissingSession() {
        String refreshToken = "refresh-token";
        when(authSessionRepository.findByRefreshTokenHash(TokenHasher.sha256(refreshToken)))
            .thenReturn(Optional.empty());

        authService.logout(refreshToken);

        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }

    private AppUser buildUser(String email) {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        Role role = new Role();
        role.setName("client");
        user.setRoles(Set.of(role));
        return user;
    }

    private AuthSession buildSession(AppUser user, Instant expiresAt) {
        AuthSession session = new AuthSession();
        session.setId(100L);
        session.setUser(user);
        session.setRefreshTokenHash(TokenHasher.sha256("stored"));
        session.setExpiresAt(expiresAt);
        return session;
    }

    private Role clientRole(Long id) {
        Role role = new Role();
        role.setId(id);
        role.setName("client");
        return role;
    }

}
