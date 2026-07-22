package com.aphinity.client_analytics_core.api.auth.services;

/** Application-owned boundary for authentication notifications. */
public interface AuthNotificationPort {
    void sendVerificationCode(Long userId, String email, String code, long ttlSeconds);

    void sendRecoveryCode(Long userId, String email, String code, long ttlSeconds);
}
