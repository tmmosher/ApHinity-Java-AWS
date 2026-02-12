package com.aphinity.client_analytics_core.api.core.response;

import com.aphinity.client_analytics_core.api.core.entities.LocationMemberRole;

import java.time.Instant;

/**
 * Membership payload tying a user to a location.
 *
 * @param locationId location id
 * @param userId user id
 * @param userEmail member email
 * @param userRole membership role within location
 * @param createdAt membership creation timestamp
 */
public record LocationMembershipResponse(
    Long locationId,
    Long userId,
    String userEmail,
    LocationMemberRole userRole,
    Instant createdAt
) {
}
