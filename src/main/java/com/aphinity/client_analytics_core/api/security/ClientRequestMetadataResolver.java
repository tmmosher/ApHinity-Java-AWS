package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientRequestMetadataResolver {
    private final Set<String> trustedProxies;

    public ClientRequestMetadataResolver(
        @Value("${app.security.trusted-proxies:127.0.0.1,::1}") String trustedProxiesValue
    ) {
        this.trustedProxies = Arrays.stream(trustedProxiesValue.split(","))
            .map(String::strip)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

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

    public String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && trustedProxies.contains(remoteAddr.strip());
    }
}
