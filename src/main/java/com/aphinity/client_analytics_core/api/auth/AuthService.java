package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.entities.Role;
import com.aphinity.client_analytics_core.api.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.security.CaptchaVerificationService;
import com.aphinity.client_analytics_core.api.security.JwtService;
import com.aphinity.client_analytics_core.api.entities.AppUser;
import com.aphinity.client_analytics_core.api.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.logging.AppLoggingProperties;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.servlet.http.HttpServletResponse;
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
    private final CaptchaVerificationService captchaVerificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        LoginAttemptService loginAttemptService,
        CaptchaVerificationService captchaVerificationService
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.captchaVerificationService = captchaVerificationService;
    }

    private Map<Pattern, String> buildPasswordRequirements() {
        Map<Pattern, String> requirements = new LinkedHashMap<>();
        requirements.put(LEN_8_PLUS, "Must be at least 8 characters");
        requirements.put(HAS_DIGIT, "Must contain at least one digit");
        requirements.put(HAS_LETTER, "Must contain at least one letter");
        requirements.put(HAS_SPECIAL, "Must contain at least one special character");
        return Map.copyOf(requirements);
    }

    @Transactional
    public IssuedTokens login(
        String email,
        String password,
        String ipAddress,
        String userAgent,
        String captchaToken
    ) {
        if (loginAttemptService.isCaptchaRequired(email)) {
            captchaVerificationService.verify(captchaToken, ipAddress);
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

    @Transactional
    public void signup(String email, String password, String name,
                       String ipAddress, String userAgent) {
        for (Map.Entry<Pattern, String> requirement : passwordRequirements.entrySet()) {
            if (!requirement.getKey().matcher(password).find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, requirement.getValue());
            }
        }
        boolean isAphinity = email.toLowerCase().strip().endsWith("@aphinitytech.com");
        AppUser user = buildUser(email, password, isAphinity, name);
        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw userAlreadyExists();
        }
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
}
