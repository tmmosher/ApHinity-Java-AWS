package com.aphinity.client_analytics_core.api.core.response;

import java.util.List;

/**
 * Paginated admin-facing user role payload.
 *
 * @param users current page of users
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total number of matching users
 * @param totalPages total page count
 */
public record AdminUserRolePageResponse(
    List<AdminUserRoleResponse> users,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
