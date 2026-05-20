package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphResponseMapperTest {
    @Test
    void fallsBackToProjectedTimeSeriesRangesWhenWindowTracesAreNotMaterialized() {
        Graph graph = new Graph();
        graph.setId(10L);
        graph.setName("Water Quality Compliance");
        graph.setCreatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of("2025-11-01", "2026-01-15", "2026-03-10"),
            "y", List.of(4, 7, 9),
            "customdata", List.of(
                Map.of("sampleCount", 1),
                Map.of("sampleCount", 2),
                Map.of("sampleCount", 3)
            )
        )));

        GraphResponseMapper mapper = new GraphResponseMapper(
            Clock.fixed(Instant.parse("2026-03-20T08:00:00Z"), ZoneOffset.UTC)
        );

        GraphResponse response = mapper.toResponse(graph);

        assertProjectedTrace(
            response.timeRangeData().get("oneMonth").getFirst(),
            List.of("2026-03-10"),
            List.of(9L),
            List.of(3L)
        );
        assertProjectedTrace(
            response.timeRangeData().get("threeMonths").getFirst(),
            List.of("2026-01-15", "2026-03-10"),
            List.of(7L, 9L),
            List.of(2L, 3L)
        );
    }

    @Test
    void prefersMaterializedRangePayloadsWhenTheyExist() {
        Graph graph = new Graph();
        graph.setId(11L);
        graph.setName("Derived");
        graph.setCreatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Non-Conformances",
            "x", List.of(9),
            "y", List.of("All Data")
        )));
        GraphRelationalPayloadMapper.syncGraphData(
            graph,
            List.of(Map.of(
                "type", "bar",
                "name", "Non-Conformances",
                "x", List.of(2),
                "y", List.of("Materialized 1M")
            )),
            GraphTimeRange.ONE_MONTH
        );

        GraphResponseMapper mapper = new GraphResponseMapper(
            Clock.fixed(Instant.parse("2026-03-20T08:00:00Z"), ZoneOffset.UTC)
        );

        GraphResponse response = mapper.toResponse(graph);

        assertEquals(
            List.of(Map.of(
                "type", "bar",
                "name", "Non-Conformances",
                "orientation", "h",
                "x", List.of(2L),
                "y", List.of("Materialized 1M")
            )),
            response.timeRangeData().get("oneMonth")
        );
        assertEquals(
            List.of(Map.of(
                "type", "bar",
                "name", "Non-Conformances",
                "orientation", "h",
                "x", List.of(9L),
                "y", List.of("All Data")
            )),
            response.timeRangeData().get("threeMonths")
        );
    }

    @SuppressWarnings("unchecked")
    private void assertProjectedTrace(
        Map<String, Object> trace,
        List<String> expectedX,
        List<Long> expectedY,
        List<Long> expectedSampleCounts
    ) {
        assertEquals("scatter", trace.get("type"));
        assertEquals("HPC", trace.get("name"));
        assertEquals(expectedX, trace.get("x"));
        assertEquals(
            expectedY,
            ((List<Number>) trace.get("y")).stream()
                .map(Number::longValue)
                .toList()
        );
        List<Map<String, Object>> customData = (List<Map<String, Object>>) trace.get("customdata");
        assertEquals(
            expectedSampleCounts,
            customData.stream()
                .map(entry -> ((Number) entry.get("sampleCount")).longValue())
                .toList()
        );
    }
}
