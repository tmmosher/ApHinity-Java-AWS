package com.aphinity.client_analytics_core.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Renders the SPA index template with a request-scoped CSP nonce.
 */
@Component
public class IndexHtmlTemplateRenderer {
    static final String NONCE_PLACEHOLDER = "__CSP_NONCE__";
    private static final String CLASSPATH_INDEX_HTML_PATH = "static/index.html";
    private static final Path FALLBACK_FRONTEND_INDEX_PATH = Path.of("frontend", "index.html");
    private static final Pattern NONCELESS_SCRIPT_TAG_PATTERN = Pattern.compile("<script(?![^>]*\\bnonce=)");
    private static final Pattern NONCELESS_STYLESHEET_LINK_PATTERN = Pattern.compile(
        "<link(?![^>]*\\bnonce=)(?=[^>]*\\brel=(\"|')stylesheet\\1)"
    );
    private static final Pattern NONCELESS_CSP_META_PATTERN = Pattern.compile(
        "<meta(?![^>]*\\bnonce=)(?=[^>]*\\bproperty=(\"|')csp-nonce\\1)"
    );
    private static final Pattern VIEWPORT_META_PATTERN = Pattern.compile("(<meta\\s+name=(\"|')viewport\\2[^>]*>)");

    private volatile String indexHtmlTemplate;

    public IndexHtmlTemplateRenderer() {
    }

    IndexHtmlTemplateRenderer(String indexHtmlTemplate) {
        this.indexHtmlTemplate = indexHtmlTemplate;
    }

    /**
     * @param nonce request CSP nonce
     * @return index HTML with all nonce placeholders replaced
     */
    public String renderWithNonce(String nonce) {
        String template = resolveTemplate();
        String rendered = template.contains(NONCE_PLACEHOLDER)
            ? template.replace(NONCE_PLACEHOLDER, nonce)
            : template;
        return injectNonceAttributes(rendered, nonce);
    }

    private String resolveTemplate() {
        String cachedTemplate = indexHtmlTemplate;
        if (cachedTemplate != null) {
            return cachedTemplate;
        }

        synchronized (this) {
            if (indexHtmlTemplate == null) {
                indexHtmlTemplate = loadTemplate();
            }
            return indexHtmlTemplate;
        }
    }

    private String loadTemplate() {
        String classpathTemplate = readClasspathTemplate();
        if (classpathTemplate != null) {
            return classpathTemplate;
        }

        String frontendTemplate = readFrontendTemplate();
        if (frontendTemplate != null) {
            return frontendTemplate;
        }
        throw new IllegalStateException(
            "Unable to locate frontend index template at "
                + CLASSPATH_INDEX_HTML_PATH
                + " or "
                + FALLBACK_FRONTEND_INDEX_PATH
        );
    }

    private String readClasspathTemplate() {
        ClassPathResource indexResource = new ClassPathResource(CLASSPATH_INDEX_HTML_PATH);
        if (!indexResource.exists()) {
            return null;
        }
        try {
            return StreamUtils.copyToString(indexResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load frontend index template from classpath", ex);
        }
    }

    private String readFrontendTemplate() {
        if (!Files.exists(FALLBACK_FRONTEND_INDEX_PATH)) {
            return null;
        }
        try {
            return Files.readString(FALLBACK_FRONTEND_INDEX_PATH, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load frontend index template fallback", ex);
        }
    }

    private String injectNonceAttributes(String template, String nonce) {
        String nonceAttribute = " nonce=\"" + nonce + "\"";

        String rendered = NONCELESS_SCRIPT_TAG_PATTERN
            .matcher(template)
            .replaceAll("<script" + nonceAttribute);
        rendered = NONCELESS_STYLESHEET_LINK_PATTERN
            .matcher(rendered)
            .replaceAll("<link" + nonceAttribute);

        if (rendered.contains("property=\"csp-nonce\"") || rendered.contains("property='csp-nonce'")) {
            return NONCELESS_CSP_META_PATTERN
                .matcher(rendered)
                .replaceAll("<meta" + nonceAttribute);
        }

        String nonceMetaTag = "<meta property=\"csp-nonce\" nonce=\"" + nonce + "\" />";
        if (rendered.contains("</head>")) {
            String withViewportMetaNonce = VIEWPORT_META_PATTERN
                .matcher(rendered)
                .replaceFirst("$1" + System.lineSeparator() + "    " + nonceMetaTag);
            if (!withViewportMetaNonce.equals(rendered)) {
                return withViewportMetaNonce;
            }
            return rendered.replace("</head>", "    " + nonceMetaTag + System.lineSeparator() + "  </head>");
        }
        return rendered;
    }
}
