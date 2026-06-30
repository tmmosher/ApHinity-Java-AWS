package com.aphinity.client_analytics_core.api.core.response.dashboard;

import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;

import java.util.List;

public record LocationDashboardSpreadsheetUploadResponse(
    List<GraphResponse> graphs,
    List<LocationEventRequest> correctiveActions
) {
    public LocationDashboardSpreadsheetUploadResponse {
        graphs = graphs == null ? List.of() : List.copyOf(graphs);
        correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
    }
}
