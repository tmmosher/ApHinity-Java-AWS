package com.aphinity.client_analytics_core.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexHtmlTemplateRendererTest {
    @Test
    void replacesNoncePlaceholderWhenPresent() {
        String template = """
            <html>
              <head>
                <meta property="csp-nonce" nonce="__CSP_NONCE__" />
              </head>
              <body>
                <script nonce="__CSP_NONCE__" type="module" src="/assets/index.js"></script>
              </body>
            </html>
            """;

        IndexHtmlTemplateRenderer renderer = new IndexHtmlTemplateRenderer(template);

        String rendered = renderer.renderWithNonce("nonce123");

        assertThat(rendered)
            .contains("nonce=\"nonce123\"")
            .doesNotContain("__CSP_NONCE__");
    }

    @Test
    void injectsNonceWhenTemplateLacksPlaceholder() {
        String template = """
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <link rel="stylesheet" href="/assets/index.css" />
              </head>
              <body>
                <script type="module" src="/assets/index.js"></script>
              </body>
            </html>
            """;

        IndexHtmlTemplateRenderer renderer = new IndexHtmlTemplateRenderer(template);

        String rendered = renderer.renderWithNonce("nonce123");

        assertThat(rendered)
            .contains("<meta property=\"csp-nonce\" nonce=\"nonce123\" />")
            .contains("<link nonce=\"nonce123\" rel=\"stylesheet\" href=\"/assets/index.css\" />")
            .contains("<script nonce=\"nonce123\" type=\"module\" src=\"/assets/index.js\"></script>");
    }
}
