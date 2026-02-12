package com.aphinity.client_analytics_core.api.core.response;

/**
 * Profile payload returned to authenticated clients.
 *
 * @param name display name
 * @param email account email
 * @param verified whether email verification is complete
 * @param role resolved account role
 */
public record ProfileResponse(
    String name,
    String email,
    boolean verified,
    AccountRole role
) {
}
