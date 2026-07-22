package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphNameUpdateResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardTablePageResponse;

import java.util.List;
import java.util.Map;

/** Application boundary exposed to HTTP and future section-scoped graph adapters. */
public interface LocationGraphApplication {
    List<GraphResponse> getAccessibleLocationGraphs(Long userId, Long locationId, Integer monthRange);

    LocationDashboardTablePageResponse getAccessibleLocationGraphTablePage(
        Long userId, Long locationId, Long graphId, Integer monthRange, Integer page, Integer size
    );

    GraphResponse createLocationGraph(
        Long userId, Long locationId, Long sectionId, boolean createNewSection, String graphDefinitionKey
    );

    void updateLocationGraphs(
        Long userId,
        Long locationId,
        List<GraphUpdateCommand> updates,
        Map<String, Object> sectionLayout,
        Integer monthRange
    );

    GraphNameUpdateResponse updateLocationGraphName(Long userId, Long locationId, Long graphId, String name);

    void deleteLocationGraph(Long userId, Long locationId, Long graphId);

    void deleteLocationSection(Long userId, Long locationId, Long sectionId);

    record GraphUpdateCommand(
        Long graphId,
        String description,
        Object data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style,
        String expectedUpdatedAt
    ) {
    }
}
