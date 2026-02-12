package com.aphinity.client_analytics_core.api.core.response;

import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;

import java.time.Instant;

/**
 * Invitation payload exposing lifecycle state and audit timestamps.
 *
 * @param id invite id
 * @param locationId location id
 * @param locationName location name
 * @param invitedEmail invite recipient email
 * @param invitedByUserId inviter user id
 * @param status invite status
 * @param expiresAt expiration timestamp
 * @param createdAt creation timestamp
 * @param acceptedAt acceptance timestamp
 * @param acceptedUserId accepting user id
 * @param revokedAt revocation timestamp
 */
public record LocationInviteResponse(
    Long id,
    Long locationId,
    String locationName,
    String invitedEmail,
    Long invitedByUserId,
    LocationInviteStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant acceptedAt,
    Long acceptedUserId,
    Instant revokedAt
) {
}
