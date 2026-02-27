package com.aphinity.client_analytics_core.api.security;

/**
 * Builds the strict application CSP policy with per-request script nonces.
 */
final class ContentSecurityPolicyBuilder {
    private ContentSecurityPolicyBuilder() {
    }

    /**
     * @param nonce request-scoped nonce value
     * @return serialized Content-Security-Policy header value
     */
    static String buildPolicy(String nonce) {
        return String.join(" ",
            "default-src 'self';",
            "script-src 'self' 'nonce-" + nonce + "' https://challenges.cloudflare.com https://static.cloudflareinsights.com;",
            "script-src-attr 'none';",
            "frame-src 'self' https://challenges.cloudflare.com;",
            "connect-src 'self' https://challenges.cloudflare.com https://static.cloudflareinsights.com https://cloudflareinsights.com;",
            "img-src 'self' data:;",
            "style-src 'self';",
            "style-src-elem 'self' 'unsafe-inline';",
            "style-src-attr 'unsafe-inline';",
            "font-src 'self';",
            "object-src 'none';",
            "base-uri 'self';",
            "form-action 'self';",
            "frame-ancestors 'none';"
        );
    }
}
