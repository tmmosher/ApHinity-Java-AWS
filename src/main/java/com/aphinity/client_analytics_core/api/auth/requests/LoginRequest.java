package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for email/password login.
 *
 * @param email account email address
 * @param password raw password
 * @param captchaToken optional Turnstile token when captcha is required
 */
public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    String captchaToken
) {}
