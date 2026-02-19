package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Resolves bearer tokens from either Authorization header or auth cookies.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {
    private final DefaultBearerTokenResolver defaultBearerTokenResolver = new DefaultBearerTokenResolver();

    /**
     * Extracts bearer token from request.
     * Header tokens take precedence so explicit API clients are unaffected by cookie state.
     *
     * @param request HTTP request
     * @return bearer token or {@code null} when not present
     */
    @Override
    public String resolve(HttpServletRequest request) {
        String headerToken = defaultBearerTokenResolver.resolve(request);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieNames.ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
