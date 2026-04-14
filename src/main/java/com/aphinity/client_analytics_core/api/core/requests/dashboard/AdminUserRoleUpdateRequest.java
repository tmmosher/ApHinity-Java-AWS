package com.aphinity.client_analytics_core.api.core.requests.dashboard;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for updating a user's account-level role.
 *
 * @param role target account role value
 */
public record AdminUserRoleUpdateRequest(
    @NotBlank
    String role
) {
}
