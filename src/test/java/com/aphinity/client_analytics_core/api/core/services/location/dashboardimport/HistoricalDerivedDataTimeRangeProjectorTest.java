package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalDerivedDataTimeRangeProjectorTest {
    @Test
    void projectsDerivedInputsFromSelectedWindowWithoutScatterContextMonth() {
        LocalDate priorMonth = LocalDate.parse("2026-03-31");
        LocalDate selectedBoundary = LocalDate.parse("2026-04-01");
        LocalDate current = LocalDate.parse("2026-07-20");
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(
                    priorMonth, List.of(samplePoint(priorMonth, "prior")),
                    selectedBoundary, List.of(samplePoint(selectedBoundary, "boundary")),
                    current, List.of(samplePoint(current, "current"))
                ),
                List.of(
                    nonConformance(priorMonth, "prior"),
                    nonConformance(selectedBoundary, "boundary"),
                    nonConformance(current, "current")
                ),
                List.of(
                    rawSample(priorMonth, "prior"),
                    rawSample(selectedBoundary, "boundary"),
                    rawSample(current, "current")
                )
            );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData projected =
            HistoricalDerivedDataTimeRangeProjector.project(
                historicalData,
                new DashboardGraphMonthRange(3),
                LocalDate.parse("2026-07-21")
            );

        assertEquals(
            List.of(selectedBoundary, current),
            projected.samplesByDate().keySet().stream().sorted().toList()
        );
        assertEquals(
            List.of("boundary", "current"),
            projected.nonConformances().stream()
                .map(LocationDashboardNonConformanceIncident::sampleIdentity)
                .toList()
        );
        assertEquals(
            List.of("boundary", "current"),
            projected.rawSamples().stream()
                .map(LocationDashboardDerivedGraphSupport.HistoricalRawSample::rowIdentifier)
                .toList()
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalSamplePoint samplePoint(LocalDate date, String name) {
        return new LocationDashboardDerivedGraphSupport.HistoricalSamplePoint(
            date,
            "Facility",
            name,
            "System",
            1L,
            1L
        );
    }

    private LocationDashboardNonConformanceIncident nonConformance(LocalDate date, String identity) {
        return new LocationDashboardNonConformanceIncident(
            date,
            "Facility",
            "Measurement",
            Map.of(),
            identity,
            false,
            null
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(LocalDate date, String rowIdentifier) {
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
