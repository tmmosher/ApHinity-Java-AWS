package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {
    private final AuthCookieService authCookieService = new AuthCookieService();

    @Test
    void addRefreshCookieUsesRootPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.addRefreshCookie(request, response, "refresh-token", 3600L);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.contains("Path=/"));
    }

    @Test
    void clearRefreshCookieUsesRootPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.clearRefreshCookie(request, response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie != null && setCookie.contains("Path=/"));
    }
}
