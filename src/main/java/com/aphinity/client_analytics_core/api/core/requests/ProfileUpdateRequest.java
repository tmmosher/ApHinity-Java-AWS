package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for profile metadata updates.
 *
 * @param name profile display name
 * @param email account email address
 */
public record ProfileUpdateRequest(
    @NotBlank
    @Size(max = 128)
    String name,
    @NotBlank
    @Email
    @Size(max = 320)
    String email
) {
}
