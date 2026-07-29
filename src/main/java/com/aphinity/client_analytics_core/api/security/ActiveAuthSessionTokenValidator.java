package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Rejects access tokens whose backing authentication session is no longer active. */
@Component
public class ActiveAuthSessionTokenValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_SESSION = new OAuth2Error(
        "invalid_token",
        "Access token session is inactive",
        null
    );

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public ActiveAuthSessionTokenValidator(AuthSessionRepository authSessionRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Long sessionId = toLong(token.getClaim("sid"));
        Long userId = toLong(token.getSubject());
        if (sessionId == null || userId == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }

        boolean active = authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            sessionId,
            userId,
            clock.instant()
        );
        return active
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
