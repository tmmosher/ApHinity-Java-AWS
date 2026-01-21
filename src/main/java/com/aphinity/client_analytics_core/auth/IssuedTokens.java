package com.aphinity.client_analytics_core.auth;

public record IssuedTokens(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    long refreshExpiresIn
) {
}
