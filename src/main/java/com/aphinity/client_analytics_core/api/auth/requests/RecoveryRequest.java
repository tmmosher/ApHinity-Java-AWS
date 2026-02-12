package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for starting password recovery.
 *
 * @param email account email address
 * @param captchaToken required Turnstile token
 */
public record RecoveryRequest(
        @Email @NotBlank String email,
        @NotBlank String captchaToken
) {
}
