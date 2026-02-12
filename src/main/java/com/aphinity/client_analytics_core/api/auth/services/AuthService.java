package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.security.PasswordPolicyValidator;
import com.aphinity.client_analytics_core.api.security.JwtService;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates auth-domain workflows for login, signup, session refresh, logout, and
 * password recovery.
 * This service is the single point where security-sensitive policies are enforced:
 * captcha checks, password policy validation, refresh token rotation, and single-use
 * recovery code verification.
 */
@Service
public class AuthService {
    // constants
    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String PASSWORD_RESET_TOKEN_TABLE = "password_reset_token";
    private static final String EMAIL_VERIFICATION_TOKEN_TABLE = "email_verification_token";

    // services
    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final LoginAttemptService loginAttemptService;
    private final TurnstileValidationService turnstileValidationService;
    private final JdbcTemplate jdbcTemplate;
    private final MailSendingService mailSendingService;
    private final AsyncLogService asyncLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.recovery.token-ttl-seconds:3600}")
    private long recoveryTokenTtlSeconds;

    @Value("${app.verification.token-ttl-seconds:600}")
    private long verificationTokenTtlSeconds;

    @Value("${app.recovery.code-length:6}")
    private int recoveryCodeLength;

    public AuthService(
            AppUserRepository appUserRepository,
            AuthSessionRepository authSessionRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordPolicyValidator passwordPolicyValidator,
            LoginAttemptService loginAttemptService,
            TurnstileValidationService turnstileValidationService,
            JdbcTemplate jdbcTemplate,
            MailSendingService mailSendingService,
            AsyncLogService asyncLogService)
    {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.loginAttemptService = loginAttemptService;
        this.turnstileValidationService = turnstileValidationService;
        this.jdbcTemplate = jdbcTemplate;
        this.mailSendingService = mailSendingService;
        this.asyncLogService = asyncLogService;
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
        // Escalate to captcha only when recent failures exceed configured thresholds.
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
            // Persist refresh sessions as token hashes so raw refresh secrets never hit storage.
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
                // Only credential failures should increase captcha pressure for this email.
                loginAttemptService.recordFailure(email);
            }
            throw ex;
        }
    }

    /**
     * Registers a new user, enforcing password requirements and assigning roles.
     * Signup always creates the lowest-privilege account role.
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
        passwordPolicyValidator.validateOrThrow(password);
        AppUser user = buildUser(email, password, name);
        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw userAlreadyExists();
        }
        asyncLogService.log("User with email: '" + email + "' created at ipv4: '"
                + ipAddress + "' from agent '" + userAgent + "'.");

        String verificationCode = issueVerificationCode(user.getId());
        Runnable sendEmail = () -> {
            try {
                mailSendingService.sendVerificationEmail(
                    user.getEmail(),
                    verificationCode,
                    verificationTokenTtlSeconds
                );
            } catch (MailException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to send verification email", ex);
            }
        };
        runAfterCommit(sendEmail);
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
        // If a rotated/revoked refresh token is reused, revoke all active sessions for this user.
        // This mitigates replay after token theft.
        if (session.getRevokedAt() != null || session.getReplacedBySessionId() != null) {
            authSessionRepository.revokeAllActiveForUser(session.getUser().getId(), now);
            throw invalidRefreshToken();
        }

        if (!session.getExpiresAt().isAfter(now)) {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
            throw invalidRefreshToken();
        }

        // Rotate token material on every refresh to enforce one-time refresh token semantics.
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

    /**
     * Creates a one-time recovery code and delivers it to the account email.
     * The method is intentionally tolerant of unknown emails to avoid account enumeration.
     *
     * @param email normalized email address for the user
     * @param captchaToken Turnstile token. Captcha is always required for email recovery
     * @param ipAddress IP Address of the request
     */
    @Transactional
    public void recovery(String email, String captchaToken, String ipAddress) {
        // Recovery always requires captcha because the endpoint can be abused at scale.
        boolean validCaptcha;
        try {
            validCaptcha = turnstileValidationService.validateTurnstileResponse(captchaToken, ipAddress);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha not configured", ex);
        }
        if (!validCaptcha) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid captcha token");
        }
        AppUser user = appUserRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Do not disclose whether the email exists.
            return;
        }

        String recoveryCode = issueRecoveryCode(user.getId());
        if (recoveryCode == null) {
            // Keep endpoint response stable if a race created a token first.
            return;
        }

        Runnable sendEmail = () -> {
            try {
                mailSendingService.sendRecoveryEmail(
                    user.getEmail(),
                    recoveryCode,
                    recoveryTokenTtlSeconds
                );
            } catch (MailException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to send recovery email", ex);
            }
        };
        runAfterCommit(sendEmail);
    }

    /**
     * Verifies and consumes a one-time code, then issues a new authenticated session.
     * <p>
     * Codes are single-use: token consumption happens with an update guard so concurrent
     * requests cannot both succeed.
     *
     * @param email normalized email address for the user
     * @param recoveryCode stripped one-time code provided by the user
     * @param ipAddress IP address of the request
     * @param userAgent User agent of the request
     * @return new tokens authenticating the user for access
     */
    @Transactional
    public IssuedTokens verify(String email, String recoveryCode, String ipAddress, String userAgent) {
        String normalizedCode = normalizeRecoveryCode(recoveryCode);
        if (normalizedCode == null) {
            throw invalidRecoveryCode();
        }
        AppUser user = appUserRepository.findByEmail(email).orElseThrow(this::invalidRecoveryCode);

        String tokenHash = TokenHasher.sha256(normalizedCode);
        Instant now = Instant.now();
        // Accept both verification and recovery channels for /verify while keeping each token
        // table isolated for storage and auditing purposes.
        boolean consumed = consumeOneTimeCode(user.getId(), tokenHash, now, EMAIL_VERIFICATION_TOKEN_TABLE)
            || consumeOneTimeCode(user.getId(), tokenHash, now, PASSWORD_RESET_TOKEN_TABLE);
        if (!consumed) {
            throw invalidRecoveryCode();
        }

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            appUserRepository.save(user);
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
    private AppUser buildUser(String email, String password, String name) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(Instant.now());
        user.setName(name);
        Set<Role> roles = new HashSet<>();
        roles.add(requiredRole("client"));
        user.setRoles(roles);
        return user;
    }

    /**
     * Loads a required role and fails fast when role seed data is missing.
     */
    private Role requiredRole(String roleName) {
        return roleRepository.findByName(roleName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Role configuration invalid"));
    }

    /**
     * Generates a URL-safe, unpadded random refresh token.
     */
    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * @return A random numeric recovery code
     */
    private String generateRecoveryCode() {
        int length = recoveryCodeLength;
        int lowerBound = (int) Math.pow(10, length - 1);
        int upperBound = (int) Math.pow(10, length) - 1;
        int code = lowerBound + secureRandom.nextInt(upperBound - lowerBound + 1);
        return Integer.toString(code);
    }

    /**
     * Validates recovery code format before database lookup.
     *
     * @return normalized code, or {@code null} when the format is invalid
     */
    private String normalizeRecoveryCode(String code) {
        if (code == null
                || code.isEmpty()
                || code.length() != recoveryCodeLength) {
            return null;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return null;
            }
        }
        return code;
    }

    /**
     * Issues a verification code for signup flows.
     */
    private String issueVerificationCode(Long userId) {
        return issueOneTimeCode(
            userId,
            verificationTokenTtlSeconds,
            true,
            EMAIL_VERIFICATION_TOKEN_TABLE,
            "Unable to issue verification code"
        );
    }

    /**
     * Issues a password-recovery code.
     */
    private String issueRecoveryCode(Long userId) {
        return issueOneTimeCode(
            userId,
            recoveryTokenTtlSeconds,
            false,
            PASSWORD_RESET_TOKEN_TABLE,
            "Unable to issue recovery code"
        );
    }

    /**
     * Creates a single active one-time code record for a user and returns the raw code.
     *
     * @param userId user id owning the code
     * @param ttlSeconds code time-to-live in seconds
     * @param failOnCollision whether token insert collisions should fail the request
     * @param tokenTable trusted internal table name for the token channel
     * @param issueFailureReason error reason when collisions are treated as failures
     * @return raw code, or {@code null} when a collision occurs and collisions are tolerated
     */
    private String issueOneTimeCode(
            Long userId,
            long ttlSeconds,
            boolean failOnCollision,
            String tokenTable,
            String issueFailureReason
    ) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            "update " + tokenTable + " set consumed_at = ? where user_id = ? and consumed_at is null",
            Timestamp.from(now),
            userId
        );

        String code = generateRecoveryCode();
        String tokenHash = TokenHasher.sha256(code);
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        try {
            jdbcTemplate.update(
                "insert into " + tokenTable + " (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId,
                tokenHash,
                Timestamp.from(expiresAt)
            );
        } catch (DataIntegrityViolationException ex) {
            if (!failOnCollision) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, issueFailureReason, ex);
        }
        return code;
    }

    /**
     * Tries to consume a one-time token from the provided table.
     *
     * @return {@code true} when a matching non-expired token was consumed
     */
    private boolean consumeOneTimeCode(Long userId, String tokenHash, Instant now, String tokenTable) {
        Long tokenId;
        Instant expiresAt;
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id, expires_at from " + tokenTable + " " +
                    "where user_id = ? and token_hash = ? and consumed_at is null",
                userId,
                tokenHash
            );
            tokenId = ((Number) row.get("id")).longValue();
            expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }

        // Consume-once semantics: only the first updater can mark the token as used.
        int updated = jdbcTemplate.update(
            "update " + tokenTable + " set consumed_at = ? where id = ? and consumed_at is null",
            Timestamp.from(now),
            tokenId
        );
        return updated != 0 && expiresAt.isAfter(now);
    }

    /**
     * Runs side effects after commit when a transaction is active.
     */
    private void runAfterCommit(Runnable callback) {
        // Send only after commit so recipients never get a code that failed to persist.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
            return;
        }
        callback.run();
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

    /**
     * @return standardized 401 invalid recovery code exception
     */
    private ResponseStatusException invalidRecoveryCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid recovery code");
    }
}
