package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class HistoricalDerivedDataTimeRangeProjector {
    private HistoricalDerivedDataTimeRangeProjector() {
    }

    static LocationDashboardDerivedGraphSupport.HistoricalDerivedData project(
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData,
        GraphTimeRange timeRange,
        LocalDate anchorDate
    ) {
        if (historicalData == null || timeRange == null || timeRange == GraphTimeRange.ALL_TIME) {
            return historicalData;
        }
        LocalDate windowStart = timeRange.windowStartInclusive(anchorDate);
        if (windowStart == null) {
            return historicalData;
        }

        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> filteredSamplesByDate =
            historicalData.samplesByDate().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBefore(windowStart))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()),
                    (left, right) -> left,
                    LinkedHashMap::new
                ));

        List<LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction> filteredCorrectiveActions =
            historicalData.correctiveActions().stream()
                .filter(correctiveAction -> correctiveAction != null
                    && correctiveAction.observedDate() != null
                    && !correctiveAction.observedDate().isBefore(windowStart))
                .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
            filteredSamplesByDate,
            filteredCorrectiveActions
        );
    }
}
