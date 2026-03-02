package com.aphinity.client_analytics_core.api.security;

import java.util.ArrayList;
import java.util.List;

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
        return buildPolicy(nonce, false);
    }

    static String buildPolicy(String nonce, boolean upgradeInsecureRequests) {
        List<String> directives = new ArrayList<>();
        directives.add("default-src 'self';");
        directives.add("script-src 'self' 'nonce-" + nonce + "' https://challenges.cloudflare.com https://static.cloudflareinsights.com;");
        directives.add("script-src-attr 'none';");
        directives.add("frame-src 'self' https://challenges.cloudflare.com;");
        directives.add(
            "connect-src 'self' https://challenges.cloudflare.com https://static.cloudflareinsights.com "
                + "https://cloudflareinsights.com https://*.cloudflareinsights.com;"
        );
        directives.add(
            "img-src 'self' data: https://challenges.cloudflare.com https://static.cloudflareinsights.com "
                + "https://cloudflareinsights.com https://*.cloudflareinsights.com;"
        );
        directives.add("style-src 'self';");
        directives.add("style-src-elem 'self' 'unsafe-inline';");
        directives.add("style-src-attr 'unsafe-inline';");
        directives.add("font-src 'self';");
        directives.add("object-src 'none';");
        directives.add("base-uri 'self';");
        directives.add("form-action 'self';");
        directives.add("frame-ancestors 'none';");
        if (upgradeInsecureRequests) {
            directives.add("upgrade-insecure-requests;");
        }
        return String.join(" ", directives);
    }
}
