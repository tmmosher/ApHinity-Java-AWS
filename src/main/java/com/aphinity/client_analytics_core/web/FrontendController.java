package com.aphinity.client_analytics_core.web;

import com.aphinity.client_analytics_core.api.security.CspNonceSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Controller
public class FrontendController {
    private final IndexHtmlTemplateRenderer indexHtmlTemplateRenderer;

    public FrontendController(IndexHtmlTemplateRenderer indexHtmlTemplateRenderer) {
        this.indexHtmlTemplateRenderer = indexHtmlTemplateRenderer;
    }

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<String> serveIndex(HttpServletRequest request) {
        String nonce = CspNonceSupport.getOrCreateNonce(request);
        String html = indexHtmlTemplateRenderer.renderWithNonce(nonce);
        return ResponseEntity
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
}
