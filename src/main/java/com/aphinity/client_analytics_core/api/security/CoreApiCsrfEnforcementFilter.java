package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Enforces CSRF validation for state-changing core API requests.
 *
 * Spring's OAuth2 resource-server setup skips CSRF checks for bearer-authenticated
 * requests by default. Because this application authenticates browser callers with
 * cookie-backed bearer tokens, core write endpoints still require CSRF protection.
 */
public class CoreApiCsrfEnforcementFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfTokenRepository csrfTokenRepository;

    public CoreApiCsrfEnforcementFilter(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.equals("/api/core")
            || path.startsWith("/api/core/")
            || path.equals("/core")
            || path.startsWith("/core/"));
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken expectedToken = csrfTokenRepository.loadToken(request);
        if (expectedToken == null) {
            rejectMissingOrInvalidToken(response);
            return;
        }

        String actualToken = request.getHeader(expectedToken.getHeaderName());
        if (actualToken == null || actualToken.isBlank()) {
            actualToken = request.getHeader("X-CSRF-TOKEN");
        }
        if (actualToken == null || actualToken.isBlank()) {
            actualToken = request.getParameter(expectedToken.getParameterName());
        }
        if (actualToken == null || actualToken.isBlank()) {
            rejectMissingOrInvalidToken(response);
            return;
        }
        if (!expectedToken.getToken().equals(actualToken)) {
            rejectMissingOrInvalidToken(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectMissingOrInvalidToken(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String timestamp = Instant.now().toString();
        response.getWriter().write(
            "{\"code\":\"csrf_invalid\",\"message\":\"Missing or invalid CSRF token\","
                + "\"status\":403,\"timestamp\":\"" + timestamp + "\",\"fieldErrors\":{}}"
        );
    }
}
