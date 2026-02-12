package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures CSRF tokens are materialized so the CSRF cookie is written on responses.
 */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {
    /**
     * Touches the CSRF token request attribute to trigger token generation.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain downstream filter chain
     * @throws ServletException propagated servlet exception
     * @throws IOException propagated I/O exception
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Accessing the token forces repository generation and response-cookie emission.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
