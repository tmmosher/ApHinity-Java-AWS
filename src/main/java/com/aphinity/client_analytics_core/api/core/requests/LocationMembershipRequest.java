package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for assigning a user to a location with a role.
 *
 * @param locationId target location id
 * @param userId target user id
 */
public record LocationMembershipRequest(
    @NotNull(message = "Location id is required")
    Long locationId,
    @NotNull(message = "User id is required")
    Long userId
) {
}
