package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDashboardDerivedGraphSupportTest {
    @Test
    void buildsAlphabeticalSampleConformanceSunburstFromConfiguredHierarchy() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    hierarchySample("row-2", Map.of("system", "Towers", "site", "Plant A"), "HPC", false),
                    hierarchySample("row-3", Map.of("site", "Site B"), "Copper", true),
                    hierarchySample("row-1", Map.of("system", "Towers", "site", "Plant A"), "HPC", true)
                )
            );
        LocationDashboardImportStrategyConfig.DerivedGraphConfig definition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "sample-conformance-hierarchy",
                "Sample Conformance Hierarchy",
                null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.SAMPLE_CONFORMANCE_HIERARCHY,
                "sunburst",
                List.of(
                    new LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel(
                        LocationDashboardImportStrategyConfig.DerivedGraphHierarchySource.IDENTITY,
                        "system"
                    ),
                    new LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel(
                        LocationDashboardImportStrategyConfig.DerivedGraphHierarchySource.IDENTITY,
                        "site"
                    ),
                    new LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel(
                        LocationDashboardImportStrategyConfig.DerivedGraphHierarchySource.MEASUREMENT,
                        null
                    )
                )
            );

        Map<String, Object> trace = LocationDashboardDerivedGraphSupport.buildPayload(
            definition,
            new Graph(),
            historicalData,
            List.of()
        ).getFirst();

        assertEquals("sunburst", trace.get("type"));
        assertEquals("total", trace.get("branchvalues"));
        assertEquals(false, trace.get("sort"));
        assertEquals(
            List.of(
                "Towers", "Plant A", "HPC", "Conformances", "Non-Conformances",
                "Unknown", "Site B", "Copper", "Conformances", "Non-Conformances"
            ),
            trace.get("labels")
        );
        assertEquals(List.of(2L, 2L, 2L, 1L, 1L, 1L, 1L, 1L, 1L, 0L), trace.get("values"));
        @SuppressWarnings("unchecked")
        List<String> parents = (List<String>) trace.get("parents");
        assertEquals("", parents.get(0));
        assertEquals("", parents.get(5));
    }

    @Test
    void preservesSharedSunburstLabelColorsWhenRebuildingRangePayloads() {
        Map<String, Object> existingTrace = Map.of(
            "type", "sunburst",
            "labels", List.of("System A", "Conductivity", "System B", "Conductivity"),
            "marker", Map.of(
                "colors", List.of("#1f77b4", "#9467bd", "#1f77b4", "#9467bd"),
                "line", Map.of("color", "#ffffff", "width", 2)
            )
        );
        List<LocationDashboardDerivedGraphSupport.HistoricalRawSample> samples = List.of(
            hierarchySample("row-a", Map.of("system", "System A"), "Conductivity", true),
            hierarchySample("row-b", Map.of("system", "System B"), "Conductivity", false)
        );
        List<LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel> hierarchy = List.of(
            new LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel(
                LocationDashboardImportStrategyConfig.DerivedGraphHierarchySource.IDENTITY,
                "system"
            ),
            new LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel(
                LocationDashboardImportStrategyConfig.DerivedGraphHierarchySource.MEASUREMENT,
                null
            )
        );

        Map<String, Object> rebuiltTrace = SampleConformanceSunburstBuilder.build(
            existingTrace,
            "Sample Conformance Hierarchy",
            samples,
            hierarchy
        );

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) rebuiltTrace.get("labels");
        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>) rebuiltTrace.get("marker");
        @SuppressWarnings("unchecked")
        List<String> colors = (List<String>) marker.get("colors");
        int matchingLabelCount = 0;
        for (int index = 0; index < labels.size(); index++) {
            if ("Conductivity".equals(labels.get(index))) {
                matchingLabelCount += 1;
                assertEquals("#9467bd", colors.get(index));
            }
        }
        assertEquals(2, matchingLabelCount);
        assertEquals(Map.of("color", "#ffffff", "width", 2), marker.get("line"));
    }

    @Test
    void buildsTurnaroundTimeGraphWithTwoWeekBucket() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(new LocationDashboardDerivedGraphSupport.HistoricalNonConformance(
                    LocalDate.parse("2026-06-01"),
                    "Newport Beach",
                    "HPC",
                    Map.of("pointOfUse", "Sink 1"),
                    "row-hpc",
                    true,
                    10L
                )),
                List.of()
            );

        List<Map<String, Object>> payload = LocationDashboardDerivedGraphSupport.buildPayload(
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            new Graph(),
            historicalData,
            List.of()
        );

        assertEquals(List.of(1L), payload.getFirst().get("x"));
        assertEquals(List.of("< 2 weeks"), payload.getFirst().get("y"));
    }

    @Test
    void buildsRecentSampleMeasurementsTableFromIdentityPatternAndLatestMonthlySamples() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    rawSample(LocalDate.parse("2026-05-31"), "row-hpc", "HPC", "21", "CFU.mL", false),
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", "CFU.mL", false),
                    rawSample(LocalDate.parse("2026-06-05"), "row-hpc", "HPC", "8", "CFU.mL", false),
                    rawSample(LocalDate.parse("2026-06-10"), "row-copper", "Copper", "0", true, false),
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
            ),
            LocalDate.parse("2026-07-20")
        );

        Map<String, Object> trace = payload.getFirst();
        assertEquals("table", trace.get("type"));
        assertEquals(Map.of(
            "values", List.of("Facility", "Point Of Use", "Measurement", "Observed", "Value", "Follow-ups"),
            "align", "left"
        ), trace.get("header"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cells = (Map<String, Object>) trace.get("cells");
        assertEquals(
            List.of(
                List.of("Newport Beach", "Newport Beach", "Newport Beach"),
                List.of("Sink 1", "Sink 1", "Sink 1"),
                List.of("Legionella", "Copper", "HPC"),
                List.of("2026-06-15", "2026-06-10", "2026-06-01"),
                List.of("3", "0", "12 CFU.mL"),
                List.of("", "", 1)
            ),
            cells.get("values")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) trace.get("customdata");
        assertEquals("Active", customData.getFirst().get("caStatus"));
        assertEquals(List.of(), customData.getFirst().get("followUps"));
        assertEquals("No CA Required", customData.get(1).get("caStatus"));
        assertEquals("Active", customData.get(2).get("caStatus"));
        assertEquals(List.of(Map.of("date", "2026-06-05", "value", "8 CFU.mL")), customData.get(2).get("followUps"));
    }

    @Test
    void resolvesRecentSampleTableRowWhenFollowUpChainEventuallyBecomesCompliant() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", "CFU.mL", false, false),
                    rawSample(LocalDate.parse("2026-06-05"), "row-hpc", "HPC", "9", "CFU.mL", false, true),
                    rawSample(LocalDate.parse("2026-06-10"), "row-hpc", "HPC", "2", "CFU.mL", true, false)
                )
            );

        List<Map<String, Object>> payload = LocationDashboardDerivedGraphSupport.buildPayload(
            recentSampleMeasurementsDefinition(),
            new Graph(),
            historicalData,
            List.of(),
            LocalDate.parse("2026-07-20")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) payload.getFirst().get("customdata");
        assertEquals("Resolved", customData.getFirst().get("caStatus"));
        assertEquals(
            List.of(
                Map.of("date", "2026-06-05", "value", "9 CFU.mL"),
                Map.of("date", "2026-06-10", "value", "2 CFU.mL")
            ),
            customData.getFirst().get("followUps")
        );
    }

    @Test
    void keepsRecentSampleTableRowActiveWhenFollowUpsRemainNonCompliant() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(new LocationDashboardDerivedGraphSupport.HistoricalNonConformance(
                    LocalDate.parse("2026-06-01"),
                    "Newport Beach",
                    "HPC",
                    Map.of("pointOfUse", "Sink 1"),
                    "row-hpc",
                    true,
                    4L
                )),
                List.of(
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", "CFU.mL", false, true),
                    rawSample(LocalDate.parse("2026-06-05"), "row-hpc", "HPC", "9", "CFU.mL", false, false)
                )
            );

        List<Map<String, Object>> tablePayload = LocationDashboardDerivedGraphSupport.buildPayload(
            recentSampleMeasurementsDefinition(),
            new Graph(),
            historicalData,
            List.of(),
            LocalDate.parse("2026-07-20")
        );
        List<Map<String, Object>> turnaroundPayload = LocationDashboardDerivedGraphSupport.buildPayload(
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "non-conformance-status-by-turnaround-time",
                "Non-Conformance Status",
                "Turnaround Time",
                LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME,
                "bar"
            ),
            new Graph(),
            historicalData,
            List.of()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) tablePayload.getFirst().get("customdata");
        assertEquals("Active", customData.getFirst().get("caStatus"));
        assertEquals(List.of(1L), turnaroundPayload.getFirst().get("x"));
        assertEquals(List.of("< 1 week"), turnaroundPayload.getFirst().get("y"));
    }

    @Test
    void keepsRecentSampleTableRowActiveForSameDayCompliantSample() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", "CFU.mL", false, false),
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "2", "CFU.mL", true, false)
                )
            );

        List<Map<String, Object>> payload = LocationDashboardDerivedGraphSupport.buildPayload(
            recentSampleMeasurementsDefinition(),
            new Graph(),
            historicalData,
            List.of(),
            LocalDate.parse("2026-07-20")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) payload.getFirst().get("customdata");
        assertEquals("Active", customData.getFirst().get("caStatus"));
        assertEquals(List.of(Map.of("date", "2026-06-01", "value", "2 CFU.mL")), customData.getFirst().get("followUps"));
    }

    @Test
    void resolvesRecentSampleTableRowFromCompliantFollowUpWhenInputIsUnordered() {
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
                Map.of(),
                List.of(),
                List.of(
                    rawSample(LocalDate.parse("2026-06-10"), "row-hpc", "HPC", "2", "CFU.mL", true, false),
                    rawSample(LocalDate.parse("2026-06-01"), "row-hpc", "HPC", "12", "CFU.mL", false, false),
                    rawSample(LocalDate.parse("2026-06-05"), "row-hpc", "HPC", "9", "CFU.mL", false, true)
                )
            );

        List<Map<String, Object>> payload = LocationDashboardDerivedGraphSupport.buildPayload(
            recentSampleMeasurementsDefinition(),
            new Graph(),
            historicalData,
            List.of(),
            LocalDate.parse("2026-07-20")
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customData = (List<Map<String, Object>>) payload.getFirst().get("customdata");
        assertEquals("Resolved", customData.getFirst().get("caStatus"));
        assertEquals(
            List.of(
                Map.of("date", "2026-06-05", "value", "9 CFU.mL"),
                Map.of("date", "2026-06-10", "value", "2 CFU.mL")
            ),
            customData.getFirst().get("followUps")
        );
    }

    private LocationDashboardImportStrategyConfig.DerivedGraphConfig recentSampleMeasurementsDefinition() {
        return new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
            "recent-sample-measurements",
            "Recent Sample Measurements",
            null,
            LocationDashboardImportStrategyConfig.DerivedGraphType.RECENT_SAMPLE_MEASUREMENTS,
            "table"
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(
        LocalDate observedDate,
        String rowIdentifier,
        String measurementName,
        String rawValue,
        boolean resolved
    ) {
        return rawSample(observedDate, rowIdentifier, measurementName, rawValue, false, resolved);
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(
        LocalDate observedDate,
        String rowIdentifier,
        String measurementName,
        String rawValue,
        String units,
        boolean resolved
    ) {
        return rawSample(observedDate, rowIdentifier, measurementName, rawValue, units, false, resolved);
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(
        LocalDate observedDate,
        String rowIdentifier,
        String measurementName,
        String rawValue,
        boolean compliant,
        boolean resolved
    ) {
        return rawSample(observedDate, rowIdentifier, measurementName, rawValue, null, compliant, resolved);
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample(
        LocalDate observedDate,
        String rowIdentifier,
        String measurementName,
        String rawValue,
        String units,
        boolean compliant,
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
            units,
            compliant,
            resolved
        );
    }

    private LocationDashboardDerivedGraphSupport.HistoricalRawSample hierarchySample(
        String rowIdentifier,
        Map<String, String> identityValues,
        String measurementName,
        boolean compliant
    ) {
        return new LocationDashboardDerivedGraphSupport.HistoricalRawSample(
            LocalDate.parse("2026-06-01"),
            rowIdentifier,
            identityValues,
            measurementName,
            "1",
            null,
            compliant,
            false
        );
    }
}
