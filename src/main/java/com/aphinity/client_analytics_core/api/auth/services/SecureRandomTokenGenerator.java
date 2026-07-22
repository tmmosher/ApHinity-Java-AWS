package com.aphinity.client_analytics_core.api.auth.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** SecureRandom-backed security token adapter. */
@Component
public class SecureRandomTokenGenerator implements SecurityTokenGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String urlSafeToken(int byteCount) {
        byte[] tokenBytes = new byte[byteCount];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    @Override
    public String numericCode(int digitCount) {
        int lowerBound = (int) Math.pow(10, digitCount - 1);
        int upperBound = (int) Math.pow(10, digitCount) - 1;
        return Integer.toString(lowerBound + secureRandom.nextInt(upperBound - lowerBound + 1));
    }
}
