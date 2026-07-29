package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.services.RepositoryRefreshSessionRevoker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RepositoryRefreshSessionRevokerTest {
    @Test
    void revokesActiveSessionsUsingTheApplicationClock() {
        AuthSessionRepository authSessionRepository = mock(AuthSessionRepository.class);
        Instant revokedAt = Instant.parse("2026-07-29T17:00:00Z");
        RepositoryRefreshSessionRevoker revoker = new RepositoryRefreshSessionRevoker(
            authSessionRepository,
            Clock.fixed(revokedAt, ZoneOffset.UTC)
        );

        revoker.revokeAllForUser(42L);

        verify(authSessionRepository).revokeAllActiveForUser(42L, revokedAt);
    }
}
