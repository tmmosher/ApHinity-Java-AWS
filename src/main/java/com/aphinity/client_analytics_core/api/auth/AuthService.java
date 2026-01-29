package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.entities.Role;
import com.aphinity.client_analytics_core.api.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.security.JwtService;
import com.aphinity.client_analytics_core.api.entities.AppUser;
import com.aphinity.client_analytics_core.api.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.logging.AppLoggingProperties;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Authentication service for signup, login, refresh, and logout flows.
 * Manages refresh token sessions, password requirements, and captcha enforcement.
 */
@Service
public class AuthService {
    // constants
    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private final AppLoggingProperties loggingProperties = new AppLoggingProperties();
    private final AsyncLogService asyncLogService = new AsyncLogService(loggingProperties);
    private static final Pattern LEN_8_PLUS = Pattern.compile(".{8,}");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-={};':\"\\\\|,.<>/?`~]");
    private final Map<Pattern, String> passwordRequirements = buildPasswordRequirements();

    // services
    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TurnstileValidationService turnstileValidationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AppUserRepository appUserRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService, TurnstileValidationService turnstileValidationService)
    {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.turnstileValidationService = turnstileValidationService;
    }

    /**
     * Builds a read-only map of password requirement patterns to failure messages.
     */
    private Map<Pattern, String> buildPasswordRequirements() {
        Map<Pattern, String> requirements = new LinkedHashMap<>();
        requirements.put(LEN_8_PLUS, "Must be at least 8 characters");
        requirements.put(HAS_DIGIT, "Must contain at least one digit");
        requirements.put(HAS_LETTER, "Must contain at least one letter");
        requirements.put(HAS_SPECIAL, "Must contain at least one special character");
        return Map.copyOf(requirements);
    }

    /**
     * Authenticates a user and issues access/refresh tokens.
     * If captcha is required for the email, a valid captcha token must be provided.
     *
     * @param email user email address. Already normalized.
     * @param password raw password to verify. Already normalized.
     * @param ipAddress client IP for auditing/captcha validation
     * @param userAgent client user agent for session metadata
     * @param captchaToken Turnstile token when captcha is required
     * @return issued access and refresh tokens plus TTL metadata
     * @throws ResponseStatusException when credentials are invalid, captcha is missing/invalid,
     *     or Turnstile is unavailable
     */
    @Transactional
    public IssuedTokens login(
        String email,
        String password,
        String ipAddress,
        String userAgent,
        String captchaToken)
    {
        if (loginAttemptService.isCaptchaRequired(email)) {
            if (captchaToken == null || captchaToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Captcha required");
            }
            boolean valid;
            try {
                valid = turnstileValidationService.validateTurnstileResponse(captchaToken, ipAddress);
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha not configured", ex);
            }
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Invalid captcha");
            }
        }
        try {
            AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);
            if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw invalidCredentials();
            }
            String refreshToken = generateRefreshToken();
            AuthSession session = buildSession(user, refreshToken, ipAddress, userAgent);
            authSessionRepository.save(session);

            String accessToken = jwtService.createAccessToken(user, session.getId());
            loginAttemptService.recordSuccess(email);
            asyncLogService.log("User with email: '" + email +
                    "' logged in at ipv4: '" + ipAddress +
                    "' from agent '" + userAgent + "'.");
            return new IssuedTokens(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenTtlSeconds(),
                jwtService.getRefreshTokenTtlSeconds()
            );
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                loginAttemptService.recordFailure(email);
            }
            throw ex;
        }
    }

    /**
     * Registers a new user, enforcing password requirements and assigning roles.
     * Users with the @aphinitytech.com domain receive the partner role in addition to client.
     *
     * @param email user email. Already normalized.
     * @param password user password. Already normalized.
     * @param name display name. Normalized to upper-case.
     * @param ipAddress client IP for audit logging
     * @param userAgent client user agent for audit logging
     * @throws ResponseStatusException when password requirements fail or email already exists
     */
    @Transactional
    public void signup(String email, String password, String name,
                       String ipAddress, String userAgent)
    {
        for (Map.Entry<Pattern, String> requirement : passwordRequirements.entrySet()) {
            if (!requirement.getKey().matcher(password).find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, requirement.getValue());
            }
        }
        boolean isAphinity = email.endsWith("@aphinitytech.com");
        AppUser user = buildUser(email, password, isAphinity, name);
        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw userAlreadyExists();
        }
        asyncLogService.log("User with email: '" + email + "' created at ipv4: '"
                + ipAddress + "' from agent '" + userAgent + "'.");
    }

    /**
     * Exchanges a valid refresh token for a new access/refresh pair.
     * Revokes the old session and creates a new session atomically.
     *
     * @param refreshToken raw refresh token presented by the client
     * @param ipAddress client IP for session metadata
     * @param userAgent client user agent for session metadata
     * @return newly issued access and refresh tokens
     * @throws ResponseStatusException when the refresh token is invalid or revoked
     */
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

    /**
     * Revokes an active refresh token session if it exists.
     *
     * @param refreshToken raw refresh token presented by the client
     */
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

    public void validateCaptcha(String captchaToken) {
        //TODO something with the captcha
    }

    /**
     * Builds a new refresh token session with hashed token storage and expiry metadata.
     */
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

    /**
     * Creates a new AppUser with encoded password, timestamps, and role assignments.
     */
    private AppUser buildUser(String email, String password, boolean isAphinity, String name) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(Instant.now());
        user.setName(name);
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

    /**
     * Generates a URL-safe, unpadded random refresh token.
     */
    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    // exceptions helpers
    /**
     * @return standardized 401 invalid credentials exception
     */
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    /**
     * @return standardized 401 invalid refresh token exception
     */
    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    /**
     * @return standardized 409 email already in use exception
     */
    private ResponseStatusException userAlreadyExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }
}
