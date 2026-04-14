package com.aphinity.client_analytics_core.api.core.response.dashboard;

import java.util.List;

/**
 * Paginated admin-facing user management payload.
 *
 * @param users current page of users
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total number of matching users
 * @param totalPages total page count
 */
public record AdminManagedUserPageResponse(
    List<AdminManagedUserResponse> users,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
