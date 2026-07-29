package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CookieBearerTokenResolverTest {
    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @Test
    void ignoresAccessCookieOnPublicAuthenticationPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, "revoked-cookie-token"));

        assertNull(resolver.resolve(request));
    }

    @Test
    void honorsExplicitBearerHeaderOnPublicAuthenticationPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-token");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, "revoked-cookie-token"));

        assertEquals("explicit-token", resolver.resolve(request));
    }

    @Test
    void resolvesAccessCookieOnProtectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, "cookie-token"));

        assertEquals("cookie-token", resolver.resolve(request));
    }
}
