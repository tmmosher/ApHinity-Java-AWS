package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDashboardDerivedGraphSupportTest {
    @Test
    void buildsRecentSampleMeasurementsTableFromIdentityPatternAndLatestMonthlySamples() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", false),
                    rawSample(LocalDate.parse("2026-06-05"), "row-hpc", "HPC", "8", false),
                    rawSample(LocalDate.parse("2026-06-15"), "row-legionella", "Legionella", "3", true)
                )
            );

        List<Map<String, Object>> payload = LocationDashboardDerivedGraphSupport.buildPayload(
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "recent-sample-measurements",
                "Recent Sample Measurements",
                null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.RECENT_SAMPLE_MEASUREMENTS,
                "table"
            ),
            new Graph(),
            historicalData,
            List.of(
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("facility", List.of()),
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("pointOfUse", List.of())
            )
        );

        Map<String, Object> trace = payload.getFirst();
        assertEquals("table", trace.get("type"));
        assertEquals(Map.of(
            "values", List.of("Facility", "Point Of Use", "Measurement", "Observed", "Value", "CA Status", "Follow-ups"),
            "align", "left"
        ), trace.get("header"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cells = (Map<String, Object>) trace.get("cells");
        assertEquals(
            List.of(
                List.of("Newport Beach", "Newport Beach"),
                List.of("Sink 1", "Sink 1"),
                List.of("Legionella", "HPC"),
                List.of("2026-06-15", "2026-06-05"),
                List.of("3", "8"),
                List.of("Resolved", "Active"),
                List.of("", 1)
            ),
            cells.get("values")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) trace.get("customdata");
        assertEquals("Resolved", customData.getFirst().get("caStatus"));
        assertEquals(List.of(), customData.getFirst().get("followUps"));
        assertEquals(List.of(Map.of("date", "2026-06-01", "value", "12")), customData.get(1).get("followUps"));
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(
        LocalDate observedDate,
        String rowIdentifier,
        String measurementName,
        String rawValue,
        boolean resolved
    ) {
        return new LocationDashboardDerivedGraphSupport.HistoricalRawSample(
            observedDate,
            rowIdentifier,
            Map.of(
                "facility", "Newport Beach",
                "pointOfUse", "Sink 1"
            ),
            measurementName,
            rawValue,
            false,
            resolved
        );
    }
}
