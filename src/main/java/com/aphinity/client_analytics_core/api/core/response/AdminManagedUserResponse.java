package com.aphinity.client_analytics_core.api.core.response;

/**
 * Serialized admin-facing user management payload.
 *
 * @param id user identifier
 * @param name display name
 * @param email account email
 * @param role resolved account role
 * @param pendingDeletion whether the user is currently queued for deletion
 */
public record AdminManagedUserResponse(
    Long id,
    String name,
    String email,
    AccountRole role,
    boolean pendingDeletion
) {
}
