package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveAuthSessionTokenValidatorTest {
    private static final Instant NOW = Instant.parse("2026-07-29T17:00:00Z");

    @Mock
    private AuthSessionRepository authSessionRepository;

    private ActiveAuthSessionTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ActiveAuthSessionTokenValidator(
            authSessionRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsTokenBackedByActiveSessionForSubject() {
        Jwt token = token("42", 7L);
        when(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(7L, 42L, NOW))
            .thenReturn(true);

        assertFalse(validator.validate(token).hasErrors());
        verify(authSessionRepository).existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(7L, 42L, NOW);
    }

    @Test
    void rejectsTokenWhenSessionIsRevokedOrMissing() {
        Jwt token = token("42", 7L);

        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsTokenWithoutValidSessionBinding() {
        assertTrue(validator.validate(token("not-a-user", null)).hasErrors());
        verifyNoInteractions(authSessionRepository);
    }

    private Jwt token(String subject, Long sessionId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject(subject)
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plusSeconds(60));
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.build();
    }
}
