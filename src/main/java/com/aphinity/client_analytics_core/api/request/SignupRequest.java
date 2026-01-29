package com.aphinity.client_analytics_core.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// this is genuinely the same as a LoginRequest, but it is nice for semantic reasoning in the auth controller/service
public record SignupRequest (
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotBlank String name
) {}

