package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Persists refresh-session revocation through the authentication session repository. */
@Service
public class RepositoryRefreshSessionRevoker implements RefreshSessionRevoker {
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public RepositoryRefreshSessionRevoker(AuthSessionRepository authSessionRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Override
    public void revokeAllForUser(Long userId) {
        authSessionRepository.revokeAllActiveForUser(userId, clock.instant());
    }
}
