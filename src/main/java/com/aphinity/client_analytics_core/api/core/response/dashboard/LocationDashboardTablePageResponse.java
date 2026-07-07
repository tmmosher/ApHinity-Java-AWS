package com.aphinity.client_analytics_core.api.core.response.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record LocationDashboardTablePageResponse(
    List<Map<String, Object>> data,
    @JsonProperty("last_page")
    int lastPage,
    long total,
    int page,
    int size
) {
}
