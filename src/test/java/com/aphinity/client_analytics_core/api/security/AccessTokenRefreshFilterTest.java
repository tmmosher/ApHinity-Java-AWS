package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.response.IssuedTokens;
import com.aphinity.client_analytics_core.api.auth.services.AuthCookieService;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AccessTokenRefreshFilterTest {
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private AuthService authService;

    @Mock
    private AuthCookieService authCookieService;

    @Mock
    private ClientRequestMetadataResolver requestMetadataResolver;

    @Test
    void refreshesWhenAccessCookieIsExpired() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        request.setCookies(
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, jwtWithExp(Instant.now().minusSeconds(60))),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, "refresh-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        when(requestMetadataResolver.resolveClientIp(request)).thenReturn("127.0.0.1");
        when(requestMetadataResolver.resolveUserAgent(request)).thenReturn("JUnit");
        when(authService.refresh("refresh-token", "127.0.0.1", "JUnit"))
            .thenReturn(new IssuedTokens("new-access", "new-refresh", "Bearer", 900L, 3600L));

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        assertNotNull(chain.capturedRequest);
        HttpServletRequest wrapped = (HttpServletRequest) chain.capturedRequest;
        assertEquals("Bearer new-access", wrapped.getHeader("Authorization"));
        verify(authService).refresh("refresh-token", "127.0.0.1", "JUnit");
        verify(authCookieService).addAccessCookie(request, response, "new-access", 900L);
        verify(authCookieService).addRefreshCookie(request, response, "new-refresh", 3600L);
    }

    @Test
    void doesNotRefreshWhenAccessCookieIsValid() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, jwtWithExp(Instant.now().plusSeconds(300))));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        verify(authService, never()).refresh(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotRefreshWhenAccessTokenPayloadCannotBeParsed() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setCookies(
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, "not-a-jwt"),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, "refresh-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        verify(authService, never()).refresh(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotRefreshWhenRefreshCookieMissing() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, jwtWithExp(Instant.now().minusSeconds(60))));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        verify(authService, never()).refresh(anyString(), anyString(), anyString());
        verify(authCookieService, never()).addAccessCookie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(authCookieService, never()).addRefreshCookie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void clearsCookiesWhenRefreshFailsUnauthorized() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/profile");
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, jwtWithExp(Instant.now().minusSeconds(60))),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, "invalid-refresh")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        when(requestMetadataResolver.resolveClientIp(request)).thenReturn("127.0.0.1");
        when(requestMetadataResolver.resolveUserAgent(request)).thenReturn(null);
        when(authService.refresh("invalid-refresh", "127.0.0.1", null))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        verify(authCookieService).clearAccessCookie(request, response);
        verify(authCookieService).clearRefreshCookie(request, response);
    }

    @Test
    void doesNotRunOnNonCorePaths() throws Exception {
        AccessTokenRefreshFilter filter = new AccessTokenRefreshFilter(
            authService,
            authCookieService,
            requestMetadataResolver,
            jwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setCookies(
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, jwtWithExp(Instant.now().minusSeconds(60))),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, "refresh-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called);
        verify(authService, never()).refresh(anyString(), anyString(), anyString());
        verify(requestMetadataResolver, never()).resolveClientIp(any());
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("test-issuer");
        properties.setSecret(TEST_SECRET);
        properties.setAccessTokenTtlSeconds(900L);
        properties.setRefreshTokenTtlSeconds(3600L);
        return properties;
    }

    private String jwtWithExp(Instant expiry) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .expirationTime(Date.from(expiry))
            .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }

    private static final class CapturingChain implements FilterChain {
        private boolean called;
        private ServletRequest capturedRequest;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            this.called = true;
            this.capturedRequest = request;
        }
    }
}
