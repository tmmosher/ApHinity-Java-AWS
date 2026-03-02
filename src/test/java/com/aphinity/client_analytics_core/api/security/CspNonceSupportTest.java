package com.aphinity.client_analytics_core.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CspNonceSupportTest {
    @Test
    void reusesNonceWithinSingleRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String first = CspNonceSupport.getOrCreateNonce(request);
        String second = CspNonceSupport.getOrCreateNonce(request);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void acceptsPreexistingNonceAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CspNonceSupport.REQUEST_ATTRIBUTE, "existing-nonce");

        String nonce = CspNonceSupport.getOrCreateNonce(request);

        assertThat(nonce).isEqualTo("existing-nonce");
    }

    @Test
    void generatesNonceUsingCspSafeCharset() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String nonce = CspNonceSupport.getOrCreateNonce(request);

        assertThat(nonce)
            .isNotBlank()
            .matches("^[A-Za-z0-9_-]+$");
    }
}
