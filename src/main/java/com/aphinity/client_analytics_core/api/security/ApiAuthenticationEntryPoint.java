package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Authentication entry point for API requests.
 * Returns a consistent JSON 401 payload instead of redirecting to an HTML login page.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);

    /**
     * Writes a JSON unauthorized response for unauthenticated API access.
     *
     * @param request incoming request
     * @param response response to write
     * @param authException authentication failure details
     * @throws IOException when writing the response fails
     */
    @Override
    public void commence(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull AuthenticationException authException
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        log.warn(
            "Rejected API request requiring authentication method={} path={} hasAccessCookie={} hasRefreshCookie={} reason={}",
            request.getMethod(),
            request.getRequestURI(),
            hasCookie(request, AuthCookieNames.ACCESS_COOKIE_NAME),
            hasCookie(request, AuthCookieNames.REFRESH_COOKIE_NAME),
            authException.getMessage()
        );
        // Keep payload shape aligned with ApiErrorResponse for frontend consistency.
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String timestamp = Instant.now().toString();
        response.getWriter().write(
            "{\"code\":\"authentication_required\",\"message\":\"Authentication required\","
                + "\"status\":401,\"timestamp\":\"" + timestamp + "\",\"fieldErrors\":{}}"
        );
    }

    private boolean hasCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
