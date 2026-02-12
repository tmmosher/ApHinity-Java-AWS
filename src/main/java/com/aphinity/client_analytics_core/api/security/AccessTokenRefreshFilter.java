package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class AccessTokenRefreshFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final ClientRequestMetadataResolver requestMetadataResolver;
    private final byte[] jwtSecret;

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
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/core") || path.startsWith("/api/core/") || path.equals("/core") || path.startsWith("/core/"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = extractCookieValue(request, AuthCookieNames.ACCESS_COOKIE_NAME);
        if (accessToken == null || accessToken.isBlank() || !isExpiredAccessToken(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = extractCookieValue(request, AuthCookieNames.REFRESH_COOKIE_NAME);
        if (refreshToken == null || refreshToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            IssuedTokens refreshedTokens = authService.refresh(
                refreshToken,
                requestMetadataResolver.resolveClientIp(request),
                requestMetadataResolver.resolveUserAgent(request)
            );
            authCookieService.addAccessCookie(request, response, refreshedTokens.accessToken(), refreshedTokens.expiresIn());
            authCookieService.addRefreshCookie(request, response, refreshedTokens.refreshToken(), refreshedTokens.refreshExpiresIn());

            MutableHeaderRequest wrappedRequest = new MutableHeaderRequest(request);
            wrappedRequest.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedTokens.accessToken());
            filterChain.doFilter(wrappedRequest, response);
            return;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                authCookieService.clearAccessCookie(request, response);
                authCookieService.clearRefreshCookie(request, response);
                filterChain.doFilter(request, response);
                return;
            }
            throw ex;
        }
    }

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

    private Instant extractExpirationVerified(String accessToken) throws ParseException, JOSEException {
        SignedJWT signedJwt = SignedJWT.parse(accessToken);
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

    private static final class MutableHeaderRequest extends HttpServletRequestWrapper {
        private String authorizationHeader;

        private MutableHeaderRequest(HttpServletRequest request) {
            super(request);
        }

        private void putHeader(String name, String value) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                this.authorizationHeader = value;
            }
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) && authorizationHeader != null) {
                return authorizationHeader;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) && authorizationHeader != null) {
                return Collections.enumeration(Collections.singletonList(authorizationHeader));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            if (authorizationHeader != null) {
                headerNames.add(HttpHeaders.AUTHORIZATION);
            }
            return Collections.enumeration(headerNames);
        }
    }
}
