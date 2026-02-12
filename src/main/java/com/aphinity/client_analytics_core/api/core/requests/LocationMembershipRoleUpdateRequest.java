package com.aphinity.client_analytics_core.api.core.requests;

import com.aphinity.client_analytics_core.api.core.entities.LocationMemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating a membership role.
 *
 * @param userRole desired role
 */
public record LocationMembershipRoleUpdateRequest(
    @NotNull(message = "User role is required")
    LocationMemberRole userRole
) {
}
