package com.aphinity.client_analytics_core.api.auth.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for deterministic hashing of token material before persistence.
 */
public final class TokenHasher {
    private static final HexFormat HEX = HexFormat.of();

    private TokenHasher() {
    }

    /**
     * Hashes the provided value using SHA-256 and returns lowercase hexadecimal output.
     *
     * @param value raw token value
     * @return SHA-256 hash in hex form
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
