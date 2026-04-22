package com.aphinity.client_analytics_core.api.core.requests.location;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating a location's work-order submission email.
 *
 * @param workOrderEmail destination email address, or null to clear it
 */
public record LocationWorkOrderEmailUpdateRequest(
    @Email(message = "Work order email must be valid")
    @Size(max = 256, message = "Work order email must be 256 characters or fewer")
    String workOrderEmail
) {
}
