package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Authentication entry point that redirects unauthenticated browser requests.
 */
public class RedirectAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final String redirectPath;

    /**
     * @param redirectPath target path for unauthenticated redirects
     */
    public RedirectAuthenticationEntryPoint(String redirectPath) {
        this.redirectPath = redirectPath;
    }

    /**
     * Sends a redirect to the configured login path when possible.
     *
     * @param request incoming request
     * @param response HTTP response
     * @param authException authentication failure details
     * @throws IOException when redirect cannot be written
     */
    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        if (!response.isCommitted()) {
            response.sendRedirect(redirectPath);
        }
    }
}
