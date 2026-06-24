package com.aphinity.client_analytics_core.api.core.services.location;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DashboardGraphMonthRangePayloadProjectorTest {
    private static final LocalDate ANCHOR_DATE = LocalDate.of(2026, 6, 23);

    @Test
    void projectFiltersDateBackedTimeSeriesTracesToMonthWindow() {
        Map<String, Object> trace = Map.of(
            "type", "scatter",
            "name", "Compliance",
            "x", List.of("2026-04-30", "2026-05-01", "2026-06-20"),
            "y", List.of(80, 90, 95)
        );

        List<Map<String, Object>> projectedPayload = DashboardGraphMonthRangePayloadProjector.project(
            List.of(trace),
            new DashboardGraphMonthRange(1),
            ANCHOR_DATE
        );

        assertEquals(List.of("2026-05-01", "2026-06-20"), projectedPayload.getFirst().get("x"));
        assertEquals(List.of(90, 95), projectedPayload.getFirst().get("y"));
    }

    @Test
    void projectFiltersInstantBackedTimeSeriesTracesToMonthWindow() {
        Map<String, Object> trace = Map.of(
            "type", "scatter",
            "name", "Compliance",
            "x", List.of(
                "2026-04-30T00:00:00Z",
                "2026-05-01T00:00:00Z",
                "2026-06-20T12:30:00Z"
            ),
            "y", List.of(80, 90, 95),
            "customdata", List.of(
                Map.of("sampleCount", 1),
                Map.of("sampleCount", 2),
                Map.of("sampleCount", 3)
            )
        );

        List<Map<String, Object>> projectedPayload = DashboardGraphMonthRangePayloadProjector.project(
            List.of(trace),
            new DashboardGraphMonthRange(1),
            ANCHOR_DATE
        );

        assertEquals(
            List.of("2026-05-01T00:00:00Z", "2026-06-20T12:30:00Z"),
            projectedPayload.getFirst().get("x")
        );
        assertEquals(List.of(90, 95), projectedPayload.getFirst().get("y"));
        assertEquals(
            List.of(Map.of("sampleCount", 2), Map.of("sampleCount", 3)),
            projectedPayload.getFirst().get("customdata")
        );
    }

    @Test
    void projectLeavesCategoricalScatterTracesUnchanged() {
        Map<String, Object> trace = Map.of(
            "type", "scatter",
            "name", "Status",
            "x", List.of("Open", "Closed"),
            "y", List.of(4, 7)
        );

        List<Map<String, Object>> projectedPayload = DashboardGraphMonthRangePayloadProjector.project(
            List.of(trace),
            new DashboardGraphMonthRange(1),
            ANCHOR_DATE
        );

        assertSame(trace, projectedPayload.getFirst());
    }

    @Test
    void projectLeavesAllTimePayloadUnchanged() {
        List<Map<String, Object>> payload = List.of(Map.of(
            "type", "scatter",
            "x", List.of("2026-04-30", "2026-05-01"),
            "y", List.of(80, 90)
        ));

        List<Map<String, Object>> projectedPayload = DashboardGraphMonthRangePayloadProjector.project(
            payload,
            DashboardGraphMonthRange.ALL_TIME,
            ANCHOR_DATE
        );

        assertSame(payload, projectedPayload);
    }
}
