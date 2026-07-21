package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalDerivedDataTimeRangeProjectorTest {
    @Test
    void projectsRawSamplesUsedByTimeRangeBoundDerivedGraphs() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    sample(LocalDate.parse("2026-02-28"), "old"),
                    sample(LocalDate.parse("2026-03-01"), "boundary"),
                    sample(LocalDate.parse("2026-07-20"), "current")
                )
            );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData projected =
            HistoricalDerivedDataTimeRangeProjector.project(
                historicalData,
                new DashboardGraphMonthRange(3),
                LocalDate.parse("2026-07-21")
            );

        assertEquals(
            List.of("boundary", "current"),
            projected.rawSamples().stream()
                .map(LocationDashboardDerivedGraphSupport.HistoricalRawSample::rowIdentifier)
                .toList()
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample sample(LocalDate date, String rowIdentifier) {
        return new LocationDashboardDerivedGraphSupport.HistoricalRawSample(
            date,
            rowIdentifier,
            Map.of("system", "Towers", "site", "Plant A"),
            "HPC",
            "1",
            null,
            true,
            false
        );
    }
}
