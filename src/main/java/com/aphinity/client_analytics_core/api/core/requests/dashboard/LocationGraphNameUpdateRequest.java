package com.aphinity.client_analytics_core.api.core.requests.dashboard;

/**
 * Request payload for renaming a single graph assigned to a location.
 *
 * @param name desired graph display name
 */
public record LocationGraphNameUpdateRequest(
    String name
) {
}
