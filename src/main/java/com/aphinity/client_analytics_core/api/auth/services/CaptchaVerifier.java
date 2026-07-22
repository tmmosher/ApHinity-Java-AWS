package com.aphinity.client_analytics_core.api.auth.services;

/** Application-owned boundary for bot-challenge verification. */
public interface CaptchaVerifier {
    boolean verify(String token, String ipAddress);
}
