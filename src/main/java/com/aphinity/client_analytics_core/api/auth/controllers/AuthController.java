package com.aphinity.client_analytics_core.api.auth.controllers;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.requests.LoginRequest;
import com.aphinity.client_analytics_core.api.auth.requests.RecoveryRequest;
import com.aphinity.client_analytics_core.api.auth.requests.VerifyRequest;
import com.aphinity.client_analytics_core.api.auth.requests.SignupRequest;
import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.security.ClientRequestMetadataResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * REST entry points for authentication and account recovery flows.
 * The controller is intentionally thin: it normalizes raw input, resolves request metadata,
 * delegates business decisions to {@link AuthService}, and writes auth cookies via
 * {@link AuthCookieService}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    public AuthController(
        AuthService authService,
        AuthCookieService authCookieService,
        ClientRequestMetadataResolver requestMetadataResolver
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.requestMetadataResolver = requestMetadataResolver;
    }

    /**
     * Authenticates a user and issues fresh access/refresh cookies.
     *
     * @param request validated login payload
     * @param httpRequest servlet request used for metadata resolution and secure-cookie detection
     * @param httpResponse servlet response used to write auth cookies
     * @return HTTP 204 when credentials are accepted
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        IssuedTokens tokens = authService.login(
            request.email().strip().toLowerCase(Locale.ROOT),
            request.password().strip(),
            requestMetadataResolver.resolveClientIp(httpRequest),
            requestMetadataResolver.resolveUserAgent(httpRequest),
            request.captchaToken()
        );
        authCookieService.addAccessCookie(httpRequest, httpResponse, tokens.accessToken(), tokens.expiresIn());
        authCookieService.addRefreshCookie(httpRequest, httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates an account but does not create an authenticated session.
     * Signup is intentionally separated from token issuance so verification policy can be enforced
     * independently.
     *
     * @param request validated signup payload
     * @param httpRequest servlet request used for audit metadata resolution
     * @return success message when account creation succeeds
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest
    ) {
        authService.signup(
            request.email().strip().toLowerCase(Locale.ROOT),
            request.password().strip(),
            request.name().strip().toUpperCase(),
            requestMetadataResolver.resolveClientIp(httpRequest),
            requestMetadataResolver.resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok("Account created successfully.");
    }

    /**
     * Rotates refresh tokens and re-issues access credentials.
     *
     * @param httpRequest servlet request containing the refresh cookie
     * @param httpResponse servlet response used to write rotated cookies
     * @return HTTP 204 when the refresh token is valid
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest httpRequest,
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
            requestMetadataResolver.resolveClientIp(httpRequest),
            requestMetadataResolver.resolveUserAgent(httpRequest)
        );
        authCookieService.addAccessCookie(httpRequest, httpResponse, tokens.accessToken(), tokens.expiresIn());
        authCookieService.addRefreshCookie(httpRequest, httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes the current refresh session (if present) and removes auth cookies from the client.
     *
     * @param httpRequest servlet request used to locate the refresh token cookie
     * @param httpResponse servlet response used to clear auth cookies
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(httpRequest);
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        authCookieService.clearRefreshCookie(httpRequest, httpResponse);
        authCookieService.clearAccessCookie(httpRequest, httpResponse);
    }

    /**
     * Starts password recovery by validating captcha and creating a one-time recovery code.
     *
     * @param request validated recovery payload
     * @param httpRequest servlet request used for client IP resolution
     * @return success message when processing is complete
     */
    @PostMapping("/recovery")
    public ResponseEntity<String> recovery(
            @Valid @RequestBody RecoveryRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String captchaToken = request.captchaToken().strip();
        String ipAddress = requestMetadataResolver.resolveClientIp(httpRequest);
        authService.recovery(email, captchaToken, ipAddress);
        return ResponseEntity.ok("Recovery email sent.");
    }

    /**
     * Verifies a recovery code and issues a fresh authenticated session.
     *
     * @param request validated verification payload
     * @param httpRequest servlet request used for metadata resolution
     * @param httpResponse servlet response used to write auth cookies
     * @return HTTP 204 when verification succeeds
     */
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(
        @Valid @RequestBody VerifyRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        IssuedTokens tokens = authService.verify(
            request.email().strip().toLowerCase(Locale.ROOT),
            request.code().strip(),
            requestMetadataResolver.resolveClientIp(httpRequest),
            requestMetadataResolver.resolveUserAgent(httpRequest)
        );
        authCookieService.addAccessCookie(httpRequest, httpResponse, tokens.accessToken(), tokens.expiresIn());
        authCookieService.addRefreshCookie(httpRequest, httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts the refresh token from cookies without failing when cookies are absent.
     *
     * @param request incoming servlet request
     * @return raw refresh token value, or {@code null} when no refresh cookie is present
     */
    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieNames.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
