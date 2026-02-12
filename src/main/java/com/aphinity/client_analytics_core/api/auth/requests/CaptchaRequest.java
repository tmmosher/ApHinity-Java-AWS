package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.NotBlank;

/**
 * Generic payload containing a captcha token.
 *
 * @param captchaToken Turnstile token provided by the client
 */
public record CaptchaRequest(
        @NotBlank String captchaToken
) {
}
