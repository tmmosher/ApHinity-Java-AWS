package com.aphinity.client_analytics_core.api.controller;

import com.aphinity.client_analytics_core.api.request.CaptchaRequest;
import com.aphinity.client_analytics_core.api.request.LoginRequest;
import com.aphinity.client_analytics_core.api.request.SignupRequest;
import com.aphinity.client_analytics_core.api.response.AuthTokensResponse;
import com.aphinity.client_analytics_core.api.auth.AuthService;
import com.aphinity.client_analytics_core.api.auth.IssuedTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE_NAME = "aphinity_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String REFRESH_COOKIE_SAME_SITE = "Strict";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthTokensResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        IssuedTokens tokens = authService.login(
            request.email().strip().toLowerCase(Locale.ROOT),
            request.password().strip(),
            extractIp(httpRequest),
            extractUserAgent(httpRequest),
            request.captchaToken()
        );
        addRefreshCookie(httpRequest, httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());
        return toAuthTokenResponse(tokens);
    }

    // Signing up won't grant auth tokens, this is so I can
    // do email verification later.
    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest
    ) {
        authService.signup(
            request.email().strip().toLowerCase(Locale.ROOT),
            request.password().strip(),
            request.name().strip().toUpperCase(),
            extractIp(httpRequest),
            extractUserAgent(httpRequest)
        );
        return ResponseEntity.ok("Account created successfully.");
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse
    ) {
        String refreshToken = extractRefreshToken(httpRequest);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing refresh token"
            );
        }
        IssuedTokens tokens = authService.refresh(
            refreshToken,
            extractIp(httpRequest),
            extractUserAgent(httpRequest)
        );
        addRefreshCookie(httpRequest, httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());
        return toAuthTokenResponse(tokens);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(httpRequest);
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(httpRequest, httpResponse);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent;
    }

    private void addRefreshCookie(
        HttpServletRequest request,
        HttpServletResponse response,
        String refreshToken,
        long maxAgeSeconds
    ) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(REFRESH_COOKIE_PATH)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(REFRESH_COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path(REFRESH_COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .sameSite(REFRESH_COOKIE_SAME_SITE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }

    private AuthTokensResponse toAuthTokenResponse(IssuedTokens tokens) {
        return new AuthTokensResponse(
            tokens.accessToken(),
            tokens.tokenType(),
            tokens.expiresIn(),
            tokens.refreshExpiresIn()
        );
    }
}
