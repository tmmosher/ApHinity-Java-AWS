package com.aphinity.client_analytics_core.api.auth.services;

/** Application boundary for cryptographically secure token material. */
public interface SecurityTokenGenerator {
    String urlSafeToken(int byteCount);

    String numericCode(int digitCount);
}
