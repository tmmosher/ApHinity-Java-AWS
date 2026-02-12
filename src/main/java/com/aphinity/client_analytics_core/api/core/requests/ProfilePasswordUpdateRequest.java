package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for authenticated password change.
 *
 * @param currentPassword current password for verification
 * @param newPassword desired new password
 */
public record ProfilePasswordUpdateRequest(
    @NotBlank
    String currentPassword,
    @NotBlank
    String newPassword
) {
}
