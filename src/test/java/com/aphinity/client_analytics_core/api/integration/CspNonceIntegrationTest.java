package com.aphinity.client_analytics_core.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CspNonceIntegrationTest extends AbstractApiIntegrationTest {
    private static final Pattern SCRIPT_NONCE_PATTERN = Pattern.compile(
        "script-src 'self' 'nonce-([A-Za-z0-9_-]+)' https://challenges.cloudflare.com;"
    );
    private static final Pattern META_NONCE_PATTERN = Pattern.compile(
        "<meta property=\"csp-nonce\" nonce=\"([A-Za-z0-9_-]+)\""
    );
    private static final Pattern SCRIPT_TAG_NONCE_PATTERN = Pattern.compile(
        "<script nonce=\"([A-Za-z0-9_-]+)\""
    );

    @Test
    void indexResponseUsesSameNonceInHtmlAndCspHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String cspHeader = result.getResponse().getHeader("Content-Security-Policy");

        String cspNonce = extractNonce(SCRIPT_NONCE_PATTERN, cspHeader);
        String metaNonce = extractNonce(META_NONCE_PATTERN, responseBody);
        String scriptTagNonce = extractNonce(SCRIPT_TAG_NONCE_PATTERN, responseBody);

        assertThat(responseBody).doesNotContain("__CSP_NONCE__");
        assertThat(metaNonce).isEqualTo(cspNonce);
        assertThat(scriptTagNonce).isEqualTo(cspNonce);
    }

    @Test
    void indexResponseRotatesNonceBetweenRequests() throws Exception {
        String firstNonce = requestRootAndExtractCspNonce();
        String secondNonce = requestRootAndExtractCspNonce();

        assertThat(firstNonce).isNotEqualTo(secondNonce);
    }

    private String requestRootAndExtractCspNonce() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn();
        String cspHeader = result.getResponse().getHeader("Content-Security-Policy");
        return extractNonce(SCRIPT_NONCE_PATTERN, cspHeader);
    }

    private String extractNonce(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
