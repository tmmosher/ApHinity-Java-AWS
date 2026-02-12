package com.aphinity.client_analytics_core.api.security;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * Configuration properties for JWT signing and token TTL values.
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;

    @Min(60)
    private long accessTokenTtlSeconds;

    @Min(300)
    private long refreshTokenTtlSeconds;

    /**
     * Validates that the configured HMAC secret has sufficient entropy length.
     */
    @PostConstruct
    void validateSecretLength() {
        // HS256 requires a sufficiently long secret to avoid weak keys.
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
    }

    /**
     * @return token issuer claim value
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * @param issuer token issuer claim value
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * @return raw HMAC signing secret
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @param secret raw HMAC signing secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * @return access token time-to-live in seconds
     */
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /**
     * @param accessTokenTtlSeconds access token time-to-live in seconds
     */
    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /**
     * @return refresh token time-to-live in seconds
     */
    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    /**
     * @param refreshTokenTtlSeconds refresh token time-to-live in seconds
     */
    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }
}
