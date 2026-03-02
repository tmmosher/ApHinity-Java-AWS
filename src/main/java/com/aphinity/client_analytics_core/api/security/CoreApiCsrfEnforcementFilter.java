package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CoreApiCsrfEnforcementFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfTokenRepository csrfTokenRepository;
    private final AsyncLogService asyncLogService;

    public CoreApiCsrfEnforcementFilter(CsrfTokenRepository csrfTokenRepository, AsyncLogService asyncLogService) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.asyncLogService = asyncLogService;
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
            rejectMissingOrInvalidToken(request, response, "missing_expected_token");
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
            rejectMissingOrInvalidToken(request, response, "missing_request_token");
            return;
        }
        if (!expectedToken.getToken().equals(actualToken)) {
            rejectMissingOrInvalidToken(request, response, "token_mismatch");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectMissingOrInvalidToken(
        HttpServletRequest request,
        HttpServletResponse response,
        String reason
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        log.warn(
            "Rejected core API request due to CSRF validation failure method={} path={} reason={}",
            method,
            path,
            reason
        );
        asyncLogService.log(
            "CoreApiCsrfEnforcementFilter rejected request method="
                + method
                + " path="
                + path
                + " reason="
                + reason
        );
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
