package com.aphinity.client_analytics_core.api.core.response;

/**
 * Serialized admin-facing user role payload.
 *
 * @param id user identifier
 * @param name display name
 * @param email account email
 * @param role resolved account role
 */
public record AdminUserRoleResponse(
    Long id,
    String name,
    String email,
    AccountRole role
) {
}
