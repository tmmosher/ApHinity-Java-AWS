package com.aphinity.client_analytics_core.api.security;

import jakarta.servlet.http.HttpServletRequest;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides per-request CSP nonce generation and storage.
 */
public final class CspNonceSupport {
    public static final String REQUEST_ATTRIBUTE = CspNonceSupport.class.getName() + ".nonce";

    private static final int NONCE_BYTE_LENGTH = 18;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder NONCE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private CspNonceSupport() {
    }

    /**
     * Reads an existing request nonce or creates a new nonce when absent.
     *
     * @param request current HTTP request
     * @return CSP nonce for this request
     */
    public static String getOrCreateNonce(HttpServletRequest request) {
        Object existingNonce = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existingNonce instanceof String nonce && !nonce.isBlank()) {
            return nonce;
        }

        String nonce = generateNonce();
        request.setAttribute(REQUEST_ATTRIBUTE, nonce);
        return nonce;
    }

    private static String generateNonce() {
        byte[] nonceBytes = new byte[NONCE_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(nonceBytes);
        return NONCE_ENCODER.encodeToString(nonceBytes);
    }
}
