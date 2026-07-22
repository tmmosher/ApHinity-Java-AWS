package com.aphinity.client_analytics_core.api.auth.services;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import org.springframework.stereotype.Component;

/** Cloudflare Turnstile adapter. */
@Component
public class TurnstileCaptchaVerifier implements CaptchaVerifier {
    private final TurnstileValidationService turnstile;

    public TurnstileCaptchaVerifier(TurnstileValidationService turnstile) {
        this.turnstile = turnstile;
    }

    @Override
    public boolean verify(String token, String ipAddress) {
        return turnstile.validateTurnstileResponse(token, ipAddress);
    }
}
