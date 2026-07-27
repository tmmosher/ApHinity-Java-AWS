package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;

import java.util.Collection;
import java.util.Map;

/** Resolves current configuration-backed graph capabilities for response adapters. */
public interface DashboardGraphCapabilityQuery {
    Map<Long, Boolean> resolveSectionTimeRangeCapabilities(String locationName, Collection<Graph> graphs);
}
