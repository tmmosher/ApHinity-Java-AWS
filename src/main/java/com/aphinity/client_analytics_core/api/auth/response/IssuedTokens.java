package com.aphinity.client_analytics_core.api.auth.response;

/**
 * Value object returned by auth workflows that issue credentials.
 *
 * @param accessToken signed JWT used for API authorization
 * @param refreshToken opaque secret used to obtain new access tokens
 * @param tokenType token scheme label (for example {@code Bearer})
 * @param expiresIn access token ttl in seconds
 * @param refreshExpiresIn refresh token ttl in seconds
 */
public record IssuedTokens(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    long refreshExpiresIn
) {
}
