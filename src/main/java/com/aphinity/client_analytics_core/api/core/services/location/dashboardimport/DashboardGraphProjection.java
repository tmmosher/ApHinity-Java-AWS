package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable projected graph payload exposed by the dashboard projection port. */
public record DashboardGraphProjection(
    List<Map<String, Object>> data,
    Map<String, Object> layout
) {
    public DashboardGraphProjection {
        data = data == null ? List.of() : List.copyOf(data);
        layout = layout == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(layout));
    }
}
