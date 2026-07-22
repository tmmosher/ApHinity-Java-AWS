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
import com.aphinity.client_analytics_core.api.core.services.dashboard.UserProfileCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Clock;
import java.util.HashSet;
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
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // constants
    private static final String TOKEN_TYPE = "Bearer";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final long ROTATED_TOKEN_REPLAY_GRACE_SECONDS = 5L;

    // services
    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaVerifier captchaVerifier;
    private final OneTimeCodeStore oneTimeCodeStore;
    private final AuthNotificationPort notificationPort;
    private final UserProfileCache userProfileCache;
    private final SecurityTokenGenerator tokenGenerator;
    private final Clock clock;

    @Value("${app.recovery.token-ttl-seconds:3600}")
    private long recoveryTokenTtlSeconds;

    @Value("${app.verification.token-ttl-seconds:600}")
    private long verificationTokenTtlSeconds;

    @Value("${app.recovery.code-length:6}")
    private int recoveryCodeLength;

    @Autowired
    public AuthService(
            AppUserRepository appUserRepository,
            AuthSessionRepository authSessionRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordPolicyValidator passwordPolicyValidator,
            LoginAttemptService loginAttemptService,
            CaptchaVerifier captchaVerifier,
            OneTimeCodeStore oneTimeCodeStore,
            AuthNotificationPort notificationPort,
            UserProfileCache userProfileCache,
            SecurityTokenGenerator tokenGenerator,
            Clock clock)
    {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.loginAttemptService = loginAttemptService;
        this.captchaVerifier = captchaVerifier;
        this.oneTimeCodeStore = oneTimeCodeStore;
        this.notificationPort = notificationPort;
        this.userProfileCache = userProfileCache;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
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
                valid = captchaVerifier.verify(captchaToken, ipAddress);
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
            log.info("User with email: '{}' logged in at ipv4: '{}' from agent '{}'.", email, ipAddress, userAgent);
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
        log.info("User with email: '{}' created at ipv4: '{}' from agent '{}'.", email, ipAddress, userAgent);

        issueAndSendVerificationCode(user.getId(), user.getEmail());
    }

    /**
     * Issues a new email verification code and queues it for delivery to the provided address.
     * Existing active verification codes for the same user are consumed first so only one
     * verification code remains active at a time.
     *
     * @param userId account id receiving the verification code
     * @param email destination email address
     */
    @Transactional
    public void issueAndSendVerificationCode(Long userId, String email) {
        String verificationCode = issueVerificationCode(userId);
        notificationPort.sendVerificationCode(
            userId,
            email,
            verificationCode,
            verificationTokenTtlSeconds
        );
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
    // Security state mutations (rotation/revocation) must persist even when the endpoint
    // responds with 401, so refresh uses noRollbackFor on response-status exceptions.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public IssuedTokens refresh(String refreshToken, String ipAddress, String userAgent) {
        String tokenHash = TokenHasher.sha256(refreshToken);
        AuthSession session = authSessionRepository.findByRefreshTokenHash(tokenHash)
            .orElseThrow(this::invalidRefreshToken);

        Instant now = clock.instant();
        // If a rotated/revoked refresh token is reused, decide whether this looks like a
        // near-simultaneous refresh race (grace period) or a suspicious replay.
        if (session.getRevokedAt() != null || session.getReplacedBySessionId() != null) {
            boolean revokeAllActiveSessions = shouldRevokeAllSessionsOnReplay(session, now);
            if (revokeAllActiveSessions) {
                authSessionRepository.revokeAllActiveForUser(session.getUser().getId(), now);
                log.warn(
                    "Suspicious refresh token replay userId={} sessionId={} replacedBySessionId={} revokedAt={}",
                    session.getUser().getId(),
                    session.getId(),
                    session.getReplacedBySessionId(),
                    session.getRevokedAt()
                );
            } else {
                log.info(
                    "Suppressing global session revocation for near-simultaneous rotated-token replay userId={} sessionId={}",
                    session.getUser().getId(),
                    session.getId()
                );
            }
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
     * Determines whether a refresh token replay should revoke all active sessions.
     * Recently rotated token replays are treated as probable client-side concurrency races.
     * Outside the grace window, replays are treated as suspicious and trigger global revocation.
     */
    private boolean shouldRevokeAllSessionsOnReplay(AuthSession replayedSession, Instant now) {
        if (replayedSession.getReplacedBySessionId() == null) {
            return true;
        }

        Instant revokedAt = replayedSession.getRevokedAt();
        if (revokedAt == null) {
            return true;
        }
        return revokedAt.plusSeconds(ROTATED_TOKEN_REPLAY_GRACE_SECONDS).isBefore(now);
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
                session.setRevokedAt(clock.instant());
                authSessionRepository.save(session);
            }
        });
    }

    /**
     * Creates a one-time recovery code and queues it for delivery to the account email.
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
            validCaptcha = captchaVerifier.verify(captchaToken, ipAddress);
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

        notificationPort.sendRecoveryCode(
            user.getId(),
            user.getEmail(),
            recoveryCode,
            recoveryTokenTtlSeconds
        );
    }

    /**
     * Verifies and consumes a one-time code, then issues a new authenticated session.
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
        Instant now = clock.instant();
        // Accept both verification and recovery channels for /verify while keeping each token
        // table isolated for storage and auditing purposes.
        boolean consumed = consumeOneTimeCode(
            user.getId(), tokenHash, now, OneTimeCodeStore.Channel.EMAIL_VERIFICATION
        ) || consumeOneTimeCode(
            user.getId(), tokenHash, now, OneTimeCodeStore.Channel.PASSWORD_RESET
        );
        if (!consumed) {
            throw invalidRecoveryCode();
        }

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            appUserRepository.save(user);
            userProfileCache.invalidate(user.getId());
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
        Instant now = clock.instant();
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
        user.setCreatedAt(clock.instant());
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
        return tokenGenerator.urlSafeToken(REFRESH_TOKEN_BYTES);
    }

    /**
     * @return A random numeric recovery code
     */
    private String generateRecoveryCode() {
        return tokenGenerator.numericCode(recoveryCodeLength);
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
            OneTimeCodeStore.Channel.EMAIL_VERIFICATION,
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
            OneTimeCodeStore.Channel.PASSWORD_RESET,
            "Unable to issue recovery code"
        );
    }

    /**
     * Creates a single active one-time code record for a user and returns the raw code.
     *
     * @param userId user id owning the code
     * @param ttlSeconds code time-to-live in seconds
     * @param failOnCollision whether token insert collisions should fail the request
     * @param channel trusted token channel
     * @param issueFailureReason error reason when collisions are treated as failures
     * @return raw code, or {@code null} when a collision occurs and collisions are tolerated
     */
    private String issueOneTimeCode(
            Long userId,
            long ttlSeconds,
            boolean failOnCollision,
            OneTimeCodeStore.Channel channel,
            String issueFailureReason
    ) {
        Instant now = clock.instant();

        String code = generateRecoveryCode();
        String tokenHash = TokenHasher.sha256(code);
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        boolean stored = oneTimeCodeStore.replaceActiveCode(userId, tokenHash, expiresAt, now, channel);
        if (!stored) {
            if (!failOnCollision) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, issueFailureReason);
        }
        return code;
    }

    /**
     * Tries to consume a one-time token from the provided table.
     *
     * @return {@code true} when a matching non-expired token was consumed
     */
    private boolean consumeOneTimeCode(
        Long userId,
        String tokenHash,
        Instant now,
        OneTimeCodeStore.Channel channel
    ) {
        return oneTimeCodeStore.consume(userId, tokenHash, now, channel);
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
