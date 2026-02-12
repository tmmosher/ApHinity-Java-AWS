package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for verifying a password recovery code.
 *
 * @param email account email address
 * @param code numeric recovery code
 */
public record VerifyRequest(
    @Email @NotBlank String email,
    @NotBlank String code
) {
}
