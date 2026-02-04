package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.NotBlank;

public record CaptchaRequest(
        @NotBlank String captchaToken
) {
}
