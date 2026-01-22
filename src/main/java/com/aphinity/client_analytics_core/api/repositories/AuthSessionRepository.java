package com.aphinity.client_analytics_core.api.repositories;

import com.aphinity.client_analytics_core.api.entities.auth.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :revokedAt where s.user.id = :userId and s.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
