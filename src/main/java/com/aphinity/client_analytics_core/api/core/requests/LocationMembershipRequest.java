package com.aphinity.client_analytics_core.api.core.requests;

import com.aphinity.client_analytics_core.api.core.entities.LocationMemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for assigning a user to a location with a role.
 *
 * @param locationId target location id
 * @param userId target user id
 * @param userRole role to assign
 */
public record LocationMembershipRequest(
    @NotNull(message = "Location id is required")
    Long locationId,
    @NotNull(message = "User id is required")
    Long userId,
    @NotNull(message = "User role is required")
    LocationMemberRole userRole
) {
}
