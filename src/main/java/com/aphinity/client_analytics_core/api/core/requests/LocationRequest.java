package com.aphinity.client_analytics_core.api.core.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for location updates.
 *
 * @param name location display name
 */
public record LocationRequest(
    @NotBlank(message = "Location name is required")
    @Size(max = 128, message = "Location name must be 128 characters or fewer")
    String name
) {
}
