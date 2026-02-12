package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves client request metadata with trusted-proxy awareness.
 */
@Component
public class ClientRequestMetadataResolver {
    private final Set<String> trustedProxies;

    /**
     * @param trustedProxiesValue comma-separated proxy addresses allowed to forward client IPs
     */
    public ClientRequestMetadataResolver(
        @Value("${app.security.trusted-proxies:127.0.0.1,::1}") String trustedProxiesValue
    ) {
        this.trustedProxies = Arrays.stream(trustedProxiesValue.split(","))
            .map(String::strip)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves the original client IP address when request came through a trusted proxy.
     *
     * @param request HTTP request
     * @return resolved client IP
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // X-Forwarded-For is a chain where the first non-empty value is the original client.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }
        for (String value : forwarded.split(",")) {
            String candidate = value.strip();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return remoteAddr;
    }

    /**
     * Resolves a non-empty User-Agent header value.
     *
     * @param request HTTP request
     * @return user agent string or {@code null} when unavailable
     */
    public String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent;
    }

    /**
     * @param remoteAddr caller network address
     * @return {@code true} when the address is configured as trusted proxy
     */
    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && trustedProxies.contains(remoteAddr.strip());
    }
}
