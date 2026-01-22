package com.aphinity.client_analytics_core.api.response;

public record AuthTokensResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    long refreshExpiresIn
) {}