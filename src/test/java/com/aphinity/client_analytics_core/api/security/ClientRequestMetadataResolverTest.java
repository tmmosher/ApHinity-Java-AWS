package com.aphinity.client_analytics_core.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientRequestMetadataResolverTest {
    @Test
    void returnsRemoteAddressWhenProxyIsUntrusted() {
        ClientRequestMetadataResolver resolver = new ClientRequestMetadataResolver("127.0.0.1,::1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        String clientIp = resolver.resolveClientIp(request);

        assertEquals("10.10.10.10", clientIp);
    }

    @Test
    void returnsForwardedAddressWhenProxyIsTrusted() {
        ClientRequestMetadataResolver resolver = new ClientRequestMetadataResolver("127.0.0.1,::1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8, 127.0.0.1");

        String clientIp = resolver.resolveClientIp(request);

        assertEquals("203.0.113.8", clientIp);
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedHeaderMissing() {
        ClientRequestMetadataResolver resolver = new ClientRequestMetadataResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        String clientIp = resolver.resolveClientIp(request);

        assertEquals("127.0.0.1", clientIp);
    }

    @Test
    void resolvesUserAgentWhenPresent() {
        ClientRequestMetadataResolver resolver = new ClientRequestMetadataResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "SolidTest");

        String userAgent = resolver.resolveUserAgent(request);

        assertEquals("SolidTest", userAgent);
    }

    @Test
    void returnsNullUserAgentWhenHeaderBlank() {
        ClientRequestMetadataResolver resolver = new ClientRequestMetadataResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "   ");

        String userAgent = resolver.resolveUserAgent(request);

        assertNull(userAgent);
    }
}
