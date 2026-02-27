package com.aphinity.client_analytics_core.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSecurityPolicyBuilderTest {
    @Test
    void policyIncludesNonceAndPlotlyCompatibleStyleDirectives() {
        String policy = ContentSecurityPolicyBuilder.buildPolicy("testNonce");

        assertThat(policy)
            .contains(
                "script-src 'self' 'nonce-testNonce' https://challenges.cloudflare.com https://static.cloudflareinsights.com;"
            )
            .contains(
                "connect-src 'self' https://challenges.cloudflare.com https://static.cloudflareinsights.com https://cloudflareinsights.com;"
            )
            .contains("style-src 'self';")
            .contains("style-src-elem 'self' 'unsafe-inline';")
            .contains("style-src-attr 'unsafe-inline';");
    }
}
