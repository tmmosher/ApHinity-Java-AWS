package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a location invite.
 *
 * @param locationId target location id
 * @param invitedEmail email address that should receive access
 */
public record LocationInviteRequest(
    @NotNull(message = "Location id is required")
    Long locationId,
    @NotBlank(message = "Invited email is required")
    @Email(message = "Invited email must be valid")
    @Size(max = 320, message = "Invited email must be 320 characters or fewer")
    String invitedEmail
) {
}
