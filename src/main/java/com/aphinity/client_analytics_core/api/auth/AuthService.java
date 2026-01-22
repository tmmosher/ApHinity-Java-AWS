package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.entities.Role;
import com.aphinity.client_analytics_core.api.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.security.JwtService;
import com.aphinity.client_analytics_core.api.entities.AppUser;
import com.aphinity.client_analytics_core.api.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.logging.AppLoggingProperties;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
    // constants
    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private final AppLoggingProperties loggingProperties = new AppLoggingProperties();
    private final AsyncLogService asyncLogService = new AsyncLogService(loggingProperties);
    private final String len_8_plus = "^.{8,}$",
            has_digit = ".*\\d.*",
            has_letter = ".*\\p{L}.",
            has_special = ".*[!@#$%^&*()_+\\-=[\\]{};':\"\\\\|,.<>/?`~].*";
    private final Map<String, String> passwordRequirements =
            Map.of(len_8_plus, "Must be at least 8 characters",
                has_digit, "Must contain at least one digit",
                has_letter, "Must contain at least one letter",
                has_special, "Must contain at least one special character");

    // services
    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public IssuedTokens login(String email, String password, String ipAddress, String userAgent) {
        AppUser user = appUserRepository.findByEmail(email)
            .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        String refreshToken = generateRefreshToken();
        AuthSession session = buildSession(user, refreshToken, ipAddress, userAgent);
        authSessionRepository.save(session);

        String accessToken = jwtService.createAccessToken(user, session.getId());
        return new IssuedTokens(
            accessToken,
            refreshToken,
            TOKEN_TYPE,
            jwtService.getAccessTokenTtlSeconds(),
            jwtService.getRefreshTokenTtlSeconds()
        );
    }

    @Transactional
    public void signup(String email, String password, String ipAddress, String userAgent) {
        AppUser potentialUser = appUserRepository.findByEmail(email).orElse(null);
        if (potentialUser != null) throw userAlreadyExists();
        for (String req : passwordRequirements.keySet()) {
            if (!password.matches(req))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, passwordRequirements.get(req));
        }
        boolean isAphinity = email.toLowerCase().endsWith("@aphinitytech.com");
        AppUser user = buildUser(email, password, isAphinity);
        appUserRepository.save(user);
        asyncLogService.log("User with email: '" + email + "' created at ipv4: '"
                + ipAddress + "' from agent '" + userAgent + "'.");
    }

    @Transactional
    public IssuedTokens refresh(String refreshToken, String ipAddress, String userAgent) {
        String tokenHash = TokenHasher.sha256(refreshToken);
        AuthSession session = authSessionRepository.findByRefreshTokenHash(tokenHash)
            .orElseThrow(this::invalidRefreshToken);

        Instant now = Instant.now();
        if (session.getRevokedAt() != null || session.getReplacedBySessionId() != null) {
            authSessionRepository.revokeAllActiveForUser(session.getUser().getId(), now);
            throw invalidRefreshToken();
        }

        if (!session.getExpiresAt().isAfter(now)) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
            throw invalidRefreshToken();
        }

        String newRefreshToken = generateRefreshToken();
        AuthSession newSession = buildSession(session.getUser(), newRefreshToken, ipAddress, userAgent);
        authSessionRepository.save(newSession);

        session.setRevokedAt(now);
        session.setReplacedBySessionId(newSession.getId());
        authSessionRepository.save(session);

        String accessToken = jwtService.createAccessToken(session.getUser(), newSession.getId());
        return new IssuedTokens(
            accessToken,
            newRefreshToken,
            TOKEN_TYPE,
            jwtService.getAccessTokenTtlSeconds(),
            jwtService.getRefreshTokenTtlSeconds()
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = TokenHasher.sha256(refreshToken);
        authSessionRepository.findByRefreshTokenHash(tokenHash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                authSessionRepository.save(session);
            }
        });
    }

    private AuthSession buildSession(AppUser user, String refreshToken, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setRefreshTokenHash(TokenHasher.sha256(refreshToken));
        session.setExpiresAt(now.plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        return session;
    }

    private AppUser buildUser(String email, String password, boolean isAphinity) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(Instant.now());
        Set<Role> roles = new HashSet<>();
        Role clientRole = new Role();
        // Roles are additive, all partners get 'client', admins get 'partner' & 'client'
        clientRole.setId(1L);
        clientRole.setName("client");
        roles.add(clientRole);
        if (isAphinity) {
            Role partnerRole = new Role();
            partnerRole.setId(2L);
            partnerRole.setName("partner");
            roles.add(partnerRole);
        }
        user.setRoles(roles);
        return user;
    }

    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    // exceptions helpers
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    private ResponseStatusException userAlreadyExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }

    private ResponseStatusException passwordTooShort() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long");
    }
}
