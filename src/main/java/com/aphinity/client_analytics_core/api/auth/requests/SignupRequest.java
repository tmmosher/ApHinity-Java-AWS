package com.aphinity.client_analytics_core.api.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for account registration.
 *
 * @param email email address for the new account
 * @param password raw password for the new account
 * @param name display name
 */
public record SignupRequest (
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotBlank String name
) {}

