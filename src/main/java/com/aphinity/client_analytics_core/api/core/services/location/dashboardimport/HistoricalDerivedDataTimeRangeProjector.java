package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;

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
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate
    ) {
        if (historicalData == null || monthRange == null || monthRange.isAllTime()) {
            return historicalData;
        }
        LocalDate windowStart = monthRange.windowStartInclusive(anchorDate);
        if (windowStart == null) {
            return historicalData;
        }
        return projectFromWindowStart(historicalData, windowStart);
    }

    private static LocationDashboardDerivedGraphSupport.HistoricalDerivedData projectFromWindowStart(
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData,
        LocalDate windowStart
    ) {
        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> filteredSamplesByDate =
            historicalData.samplesByDate().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBefore(windowStart))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()),
                    (left, right) -> left,
                    LinkedHashMap::new
                ));

        List<LocationDashboardDerivedGraphSupport.HistoricalNonConformance> filteredNonConformances =
            historicalData.nonConformances().stream()
                .filter(nonConformance -> nonConformance != null
                    && nonConformance.observedDate() != null
                    && !nonConformance.observedDate().isBefore(windowStart))
                .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
            filteredSamplesByDate,
            filteredNonConformances
        );
    }
}
