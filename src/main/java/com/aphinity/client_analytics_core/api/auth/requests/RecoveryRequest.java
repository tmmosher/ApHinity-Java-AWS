package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoveryRequest(
        @Email @NotBlank String email,
        @NotBlank String captchaToken
) {
}
