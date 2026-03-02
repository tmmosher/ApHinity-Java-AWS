package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
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
}
