package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

/**
 * Atomic application port for rebuilding persisted derived graph state.
 * Implementations participate in the caller's Spring transaction.
 */
public interface DerivedGraphRefresher {
    void refreshDerivedGraphs(Long locationId);

    void refreshImportedGraphRanges(Long locationId);
}
