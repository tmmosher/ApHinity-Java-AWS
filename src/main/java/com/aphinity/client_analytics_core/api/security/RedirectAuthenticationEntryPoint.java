package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * This class is a part of the Spring security filter chain that redirects unauthenticated users back to the login page
 */
public class RedirectAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final String redirectPath;

    public RedirectAuthenticationEntryPoint(String redirectPath) {
        this.redirectPath = redirectPath;
    }

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
