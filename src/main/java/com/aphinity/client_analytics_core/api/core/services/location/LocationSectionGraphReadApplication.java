package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationSectionGraphsResponse;

/** Read boundary for independently projected dashboard sections. */
public interface LocationSectionGraphReadApplication {
    LocationSectionGraphsResponse getAccessibleLocationSectionGraphs(
        Long userId, Long locationId, Long sectionId, Integer monthRange
    );
}
