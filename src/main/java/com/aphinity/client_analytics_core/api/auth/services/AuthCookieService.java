package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Centralizes creation and clearing of auth cookies used by API clients.
 * Cookie attributes are intentionally strict: HttpOnly to block JavaScript access,
 * SameSite strict to reduce CSRF surface, and dynamic secure flag detection for TLS
 * termination scenarios.
 */
@Service
public class AuthCookieService {
    private static final String ACCESS_COOKIE_PATH = "/";
    private static final String REFRESH_COOKIE_PATH = "/";
    private static final String COOKIE_SAME_SITE = "Strict";

    /**
     * Writes the access token cookie.
     *
     * @param request request used to infer secure transport
     * @param response response to mutate
     * @param accessToken signed JWT access token
     * @param maxAgeSeconds cookie lifetime in seconds
     */
    public void addAccessCookie(HttpServletRequest request, HttpServletResponse response, String accessToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.ACCESS_COOKIE_NAME, accessToken)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(ACCESS_COOKIE_PATH)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Writes the refresh token cookie.
     *
     * @param request request used to infer secure transport
     * @param response response to mutate
     * @param refreshToken opaque refresh secret
     * @param maxAgeSeconds cookie lifetime in seconds
     */
    public void addRefreshCookie(HttpServletRequest request, HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(REFRESH_COOKIE_PATH)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Expires the access cookie immediately.
     *
     * @param request request used to infer secure transport
     * @param response response to mutate
     */
    public void clearAccessCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.ACCESS_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(ACCESS_COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .sameSite(COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Expires the refresh cookie immediately.
     *
     * @param request request used to infer secure transport
     * @param response response to mutate
     */
    public void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(REFRESH_COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .sameSite(COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Determines whether cookies should be emitted with the {@code Secure} attribute.
     * Supports both direct TLS and reverse-proxy TLS termination via {@code X-Forwarded-Proto}.
     */
    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }
}
