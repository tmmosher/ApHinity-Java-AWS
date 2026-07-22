package com.aphinity.client_analytics_core.api.auth.services;

import java.time.Instant;

/** Persistence port for single-use authentication codes. */
public interface OneTimeCodeStore {
    enum Channel { EMAIL_VERIFICATION, PASSWORD_RESET }

    boolean replaceActiveCode(Long userId, String tokenHash, Instant expiresAt, Instant now, Channel channel);

    boolean consume(Long userId, String tokenHash, Instant now, Channel channel);
}
