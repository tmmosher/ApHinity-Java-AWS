package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.logging.AsyncLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreApiCsrfEnforcementFilterTest {
    @Mock
    private CsrfTokenRepository csrfTokenRepository;

    @Mock
    private AsyncLogService asyncLogService;

    private CoreApiCsrfEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CoreApiCsrfEnforcementFilter(csrfTokenRepository, asyncLogService);
    }

    @Test
    void shouldNotFilterSkipsNonMutatingRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/core/locations");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilterSkipsMutatingRequestsOutsideCorePaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/login");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilterEnforcesMutatingRequestsOnCorePaths() {
        MockHttpServletRequest apiCoreRequest = new MockHttpServletRequest("PUT", "/api/core/locations/1/graphs");
        MockHttpServletRequest coreRequest = new MockHttpServletRequest("DELETE", "/core/locations/1/memberships/2");

        assertFalse(filter.shouldNotFilter(apiCoreRequest));
        assertFalse(filter.shouldNotFilter(coreRequest));
    }

    @Test
    void doFilterInternalRejectsWhenExpectedTokenIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/core/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(csrfTokenRepository.loadToken(request)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"csrf_invalid\""));
        verify(chain, never()).doFilter(request, response);
        verify(asyncLogService).log(org.mockito.ArgumentMatchers.contains("missing_expected_token"));
    }

    @Test
    void doFilterInternalRejectsWhenTokenIsMissingFromRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/core/locations/1/graphs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(csrfTokenRepository.loadToken(request))
            .thenReturn(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token"));

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"csrf_invalid\""));
        verify(chain, never()).doFilter(request, response);
        verify(asyncLogService).log(org.mockito.ArgumentMatchers.contains("missing_request_token"));
    }

    @Test
    void doFilterInternalRejectsWhenHeaderTokenDoesNotMatchExpected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/core/locations/1/graphs");
        request.addHeader("X-XSRF-TOKEN", "actual-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(csrfTokenRepository.loadToken(request))
            .thenReturn(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token"));

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(request, response);
        verify(asyncLogService).log(org.mockito.ArgumentMatchers.contains("token_mismatch"));
    }

    @Test
    void doFilterInternalAcceptsTokenFromPrimaryHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/core/locations/1/graphs");
        request.addHeader("X-XSRF-TOKEN", "expected-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(csrfTokenRepository.loadToken(request))
            .thenReturn(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(asyncLogService);
    }

    @Test
    void doFilterInternalAcceptsTokenFromFallbackHeaderAndParameter() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest fallbackHeaderRequest = new MockHttpServletRequest("PUT", "/api/core/profile");
        fallbackHeaderRequest.addHeader("X-CSRF-TOKEN", "expected-token");
        MockHttpServletResponse fallbackHeaderResponse = new MockHttpServletResponse();
        when(csrfTokenRepository.loadToken(fallbackHeaderRequest))
            .thenReturn(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token"));

        filter.doFilterInternal(fallbackHeaderRequest, fallbackHeaderResponse, chain);
        verify(chain).doFilter(fallbackHeaderRequest, fallbackHeaderResponse);

        MockHttpServletRequest parameterRequest = new MockHttpServletRequest("DELETE", "/core/locations/1/memberships/2");
        parameterRequest.setParameter("_csrf", "expected-token");
        MockHttpServletResponse parameterResponse = new MockHttpServletResponse();
        when(csrfTokenRepository.loadToken(parameterRequest))
            .thenReturn(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-token"));

        filter.doFilterInternal(parameterRequest, parameterResponse, chain);
        verify(chain).doFilter(parameterRequest, parameterResponse);
    }

    @Test
    void doFilterInternalDoesNothingWhenResponseAlreadyCommitted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/core/profile");
        FilterChain chain = mock(FilterChain.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(csrfTokenRepository.loadToken(request)).thenReturn(null);
        when(response.isCommitted()).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(response, never()).setStatus(anyInt());
        verifyNoInteractions(asyncLogService);
        verify(chain, never()).doFilter(request, response);
    }
}
