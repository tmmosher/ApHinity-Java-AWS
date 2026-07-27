package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.List;

/**
 * Produces the canonical incident collection used by every non-conformance
 * dashboard metric. Implementations may change incident identity policy without
 * requiring graph builders or historical-data assembly to know import details.
 */
interface LocationDashboardNonConformanceIncidentCollector {
    List<LocationDashboardNonConformanceIncident> collect(
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples
    );
}
