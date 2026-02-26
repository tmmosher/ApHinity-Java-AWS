package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.auth.services.TokenHasher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Refreshes access tokens transparently for authenticated API requests.
 * When an expired access token cookie is present and a valid refresh token exists,
 * the filter issues new tokens and injects the refreshed bearer token into the current
 * request so downstream authentication can proceed normally.
 */
@Component
public class AccessTokenRefreshFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenRefreshFilter.class);
    private static final int REFRESH_RESULT_CACHE_MAX_SIZE = 10_000;
    private static final Duration REFRESH_RESULT_CACHE_TTL = Duration.ofSeconds(10);

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final ClientRequestMetadataResolver requestMetadataResolver;
    private final byte[] jwtSecret;
    private final Cache<String, RefreshResult> refreshResultCache;
    private final ConcurrentMap<String, CompletableFuture<RefreshResult>> inFlightRefreshes = new ConcurrentHashMap<>();

    /**
     * @param authService authentication service that performs refresh token exchange
     * @param authCookieService cookie utility for writing/clearing auth cookies
     * @param requestMetadataResolver client metadata resolver used by refresh auditing
     * @param jwtProperties JWT configuration containing the shared signing secret
     */
    public AccessTokenRefreshFilter(
        AuthService authService,
        AuthCookieService authCookieService,
        ClientRequestMetadataResolver requestMetadataResolver,
        JwtProperties jwtProperties
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.requestMetadataResolver = requestMetadataResolver;
        this.jwtSecret = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.refreshResultCache = Caffeine.newBuilder()
            .maximumSize(REFRESH_RESULT_CACHE_MAX_SIZE)
            .expireAfterWrite(REFRESH_RESULT_CACHE_TTL)
            .build();
    }

    /**
     * Restricts refresh attempts to authenticated core API routes.
     *
     * @param request incoming request
     * @return {@code true} when refresh logic should be skipped
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/core") || path.startsWith("/api/core/") || path.equals("/core") || path.startsWith("/core/"));
    }

    /**
     * Attempts refresh only when the access token is present and already expired.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain downstream chain
     * @throws ServletException propagated servlet exception
     * @throws IOException propagated I/O exception
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = extractCookieValue(request, AuthCookieNames.ACCESS_COOKIE_NAME);
        // Continue untouched unless an expired access token is present.
        if (accessToken == null || accessToken.isBlank() || !isExpiredAccessToken(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = extractCookieValue(request, AuthCookieNames.REFRESH_COOKIE_NAME);
        if (refreshToken == null || refreshToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshTokenHash = TokenHasher.sha256(refreshToken);
        RefreshResult refreshResult = resolveRefreshResult(refreshToken, refreshTokenHash, request);
        if (refreshResult.tokens() != null) {
            IssuedTokens refreshedTokens = refreshResult.tokens();
            authCookieService.addAccessCookie(request, response, refreshedTokens.accessToken(), refreshedTokens.expiresIn());
            authCookieService.addRefreshCookie(request, response, refreshedTokens.refreshToken(), refreshedTokens.refreshExpiresIn());

            // Inject Authorization header so the current request authenticates with the new token.
            MutableHeaderRequest wrappedRequest = new MutableHeaderRequest(request);
            wrappedRequest.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedTokens.accessToken());
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // Refresh token is no longer valid; clear stale cookies and continue unauthenticated.
        authCookieService.clearAccessCookie(request, response);
        authCookieService.clearRefreshCookie(request, response);
        filterChain.doFilter(request, response);
    }

    /**
     * Returns the most recent refresh result for a token hash or performs a single-flight refresh.
     *
     * Concurrent requests sharing the same refresh token wait on a single in-flight refresh
     * to avoid duplicate rotation attempts.
     */
    private RefreshResult resolveRefreshResult(
        String refreshToken,
        String refreshTokenHash,
        HttpServletRequest request
    ) {
        RefreshResult cachedResult = refreshResultCache.getIfPresent(refreshTokenHash);
        if (cachedResult != null) {
            log.debug("Using cached refresh result tokenHashPrefix={} outcome={}", tokenHashPrefix(refreshTokenHash), cachedResult.outcome());
            return cachedResult;
        }

        CompletableFuture<RefreshResult> refreshFuture = inFlightRefreshes.get(refreshTokenHash);
        if (refreshFuture == null) {
            CompletableFuture<RefreshResult> createdFuture = new CompletableFuture<>();
            CompletableFuture<RefreshResult> existingFuture = inFlightRefreshes.putIfAbsent(refreshTokenHash, createdFuture);
            refreshFuture = existingFuture != null ? existingFuture : createdFuture;

            if (existingFuture == null) {
                try {
                    RefreshResult result = refreshAccessToken(refreshToken, request, refreshTokenHash);
                    refreshResultCache.put(refreshTokenHash, result);
                    createdFuture.complete(result);
                } catch (RuntimeException ex) {
                    createdFuture.completeExceptionally(ex);
                    throw ex;
                } finally {
                    inFlightRefreshes.remove(refreshTokenHash, createdFuture);
                }
                return createdFuture.join();
            }
        }

        try {
            return refreshFuture.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unexpected refresh execution failure", cause);
        }
    }

    /**
     * Executes refresh token exchange for a single token and maps unauthorized responses
     * to a deterministic invalid-refresh result.
     */
    private RefreshResult refreshAccessToken(
        String refreshToken,
        HttpServletRequest request,
        String refreshTokenHash
    ) {
        try {
            IssuedTokens refreshedTokens = authService.refresh(
                refreshToken,
                requestMetadataResolver.resolveClientIp(request),
                requestMetadataResolver.resolveUserAgent(request)
            );
            log.debug("Refreshed access token tokenHashPrefix={} outcome={}", tokenHashPrefix(refreshTokenHash), RefreshOutcome.REFRESHED);
            return new RefreshResult(RefreshOutcome.REFRESHED, refreshedTokens);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("Refresh token invalid tokenHashPrefix={} outcome={}", tokenHashPrefix(refreshTokenHash), RefreshOutcome.INVALID_REFRESH_TOKEN);
                return new RefreshResult(RefreshOutcome.INVALID_REFRESH_TOKEN, null);
            }
            throw ex;
        }
    }

    /**
     * Returns a non-sensitive hash prefix for diagnostics.
     */
    private String tokenHashPrefix(String refreshTokenHash) {
        return refreshTokenHash.length() <= 12 ? refreshTokenHash : refreshTokenHash.substring(0, 12);
    }

    /**
     * Validates and checks whether the supplied access token has expired.
     *
     * @param accessToken raw JWT access token
     * @return {@code true} when token is expired and signature-valid
     */
    private boolean isExpiredAccessToken(String accessToken) {
        try {
            Instant expiry = extractExpirationVerified(accessToken);
            if (expiry == null) {
                return false;
            }
            Instant now = Instant.now();
            return !expiry.isAfter(now);
        } catch (ParseException | JOSEException ex) {
            return false;
        }
    }

    /**
     * Reads a cookie value by name.
     *
     * @param request request containing cookies
     * @param cookieName cookie name to resolve
     * @return cookie value or {@code null} when absent
     */
    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Parses JWT expiration only after algorithm and signature verification.
     *
     * @param accessToken raw JWT access token
     * @return expiration timestamp or {@code null} when token is invalid/incompatible
     * @throws ParseException when the token cannot be parsed
     * @throws JOSEException when cryptographic verification fails unexpectedly
     */
    private Instant extractExpirationVerified(String accessToken) throws ParseException, JOSEException {
        SignedJWT signedJwt = SignedJWT.parse(accessToken);
        // Guard against algorithm substitution by accepting only expected HS256 tokens.
        if (!JWSAlgorithm.HS256.equals(signedJwt.getHeader().getAlgorithm())) {
            return null;
        }
        MACVerifier verifier = new MACVerifier(Arrays.copyOf(jwtSecret, jwtSecret.length));
        if (!signedJwt.verify(verifier)) {
            return null;
        }
        Date expirationTime = signedJwt.getJWTClaimsSet().getExpirationTime();
        if (expirationTime == null) {
            return null;
        }
        return expirationTime.toInstant();
    }

    /**
     * Request wrapper that supports overriding the Authorization header.
     */
    private static final class MutableHeaderRequest extends HttpServletRequestWrapper {
        private String authorizationHeader;

        /**
         * @param request original servlet request
         */
        private MutableHeaderRequest(HttpServletRequest request) {
            super(request);
        }

        /**
         * Stores an Authorization header override.
         *
         * @param name header name
         * @param value header value
         */
        private void putHeader(String name, String value) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                this.authorizationHeader = value;
            }
        }

        /**
         * Resolves header by name, prioritizing the override.
         *
         * @param name header name
         * @return resolved header value
         */
        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) && authorizationHeader != null) {
                return authorizationHeader;
            }
            return super.getHeader(name);
        }

        /**
         * Resolves all values for a header name.
         *
         * @param name header name
         * @return enumeration of header values
         */
        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) && authorizationHeader != null) {
                return Collections.enumeration(Collections.singletonList(authorizationHeader));
            }
            return super.getHeaders(name);
        }

        /**
         * Returns the complete header name set including the override.
         *
         * @return header name enumeration
         */
        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            if (authorizationHeader != null) {
                headerNames.add(HttpHeaders.AUTHORIZATION);
            }
            return Collections.enumeration(headerNames);
        }
    }

    private enum RefreshOutcome {
        REFRESHED,
        INVALID_REFRESH_TOKEN
    }

    private record RefreshResult(RefreshOutcome outcome, IssuedTokens tokens) {
    }
}
