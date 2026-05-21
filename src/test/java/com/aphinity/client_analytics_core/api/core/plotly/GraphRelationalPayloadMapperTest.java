package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphCategoryPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphRelationalPayloadMapperTest {
    @Test
    void setDataSyncsRelationalTraces() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Live",
            "x", List.of("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"),
            "y", List.of(10, 20)
        )));

        assertEquals("scatter", graph.getGraphType());

        Object data = graph.getData();
        assertInstanceOf(List.class, data);
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(data);
        assertEquals(1, traces.size());
        assertEquals("scatter", traces.getFirst().get("type"));
        assertEquals("Live", traces.getFirst().get("name"));
        assertEquals(List.of("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"), traces.getFirst().get("x"));
        assertEquals(List.of(10L, 20L), traces.getFirst().get("y"));
    }

    @Test
    void setDataPreservesTimeSeriesCustomDataOnPoints() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Live",
            "x", List.of("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"),
            "y", List.of(10, 20),
            "customdata", List.of(
                Map.of("sampleCount", 1, "compliantCount", 1),
                Map.of("sampleCount", 2, "compliantCount", 1)
            )
        )));

        GraphTrace trace = graph.getGraphTraces().getFirst();
        assertEquals("time_series", trace.getDataMode());
        assertEquals(2, trace.getTimeSeriesPoints().size());
        assertEquals(
            Map.of("sampleCount", 1L, "compliantCount", 1L),
            trace.getTimeSeriesPoints().getFirst().getPointMeta().get("customdata")
        );

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(List.of(
            Map.of("sampleCount", 1L, "compliantCount", 1L),
            Map.of("sampleCount", 2L, "compliantCount", 1L)
        ), traces.getFirst().get("customdata"));
    }

    @Test
    void setDataPreservesScatterDateStringsWithFlexibleLocalDateFormatting() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "System Type Compliance",
            "x", List.of("2025-7-1", "2025-08-01"),
            "y", List.of(97, 99)
        )));

        GraphTrace trace = graph.getGraphTraces().getFirst();
        assertEquals("time_series", trace.getDataMode());

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(List.of("2025-7-1", "2025-08-01"), traces.getFirst().get("x"));
        assertEquals(List.of(97L, 99L), traces.getFirst().get("y"));
    }

    @Test
    void getDataRecoversScatterXValuesFromLegacyCategoricalPointsWithoutPointMetaX() {
        Graph graph = new Graph();
        GraphTrace trace = new GraphTrace();
        trace.setTraceKey("scatter-1");
        trace.setTraceName("System Type Compliance");
        trace.setTraceType("scatter");
        trace.setDataMode("categorical");

        GraphCategoryPoint first = new GraphCategoryPoint();
        first.setPointOrder(0);
        first.setCategoryKey("2025-7-1");
        first.setCategoryLabel("2025-7-1");
        first.setValueNumeric(BigDecimal.valueOf(97));
        first.setPointMeta(Map.of("label", "2025-7-1"));

        GraphCategoryPoint second = new GraphCategoryPoint();
        second.setPointOrder(1);
        second.setCategoryKey("2025-08-01");
        second.setCategoryLabel("2025-08-01");
        second.setValueNumeric(BigDecimal.valueOf(99));
        second.setPointMeta(Map.of("label", "2025-08-01"));

        trace.setCategoryPoints(new ArrayList<>(List.of(first, second)));
        graph.setGraphTraces(new ArrayList<>(List.of(trace)));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(List.of("2025-7-1", "2025-08-01"), traces.getFirst().get("x"));
        assertEquals(List.of(97L, 99L), traces.getFirst().get("y"));
    }

    @Test
    void getDataReturnsEmptyListWhenGraphHasNoTraces() {
        Graph graph = new Graph();

        Object data = graph.getData();
        assertInstanceOf(List.class, data);
        assertTrue(GraphPayloadMapper.toTraceList(data).isEmpty());
    }

    @Test
    void setDataReusesExistingCategoryPointEntities() {
        Graph graph = new Graph();
        GraphTrace trace = new GraphTrace();
        trace.setTraceKey("bar-1");
        trace.setTraceName("Trace 1");
        trace.setTraceType("bar");
        trace.setDataMode("categorical");

        GraphCategoryPoint point = new GraphCategoryPoint();
        point.setPointOrder(0);
        point.setCategoryKey("0");
        point.setCategoryLabel("0");
        point.setValueNumeric(java.math.BigDecimal.ONE);
        point.setPointMeta(Map.of("x", 0));

        trace.setCategoryPoints(new ArrayList<>(List.of(point)));
        graph.setGraphTraces(new ArrayList<>(List.of(trace)));

        graph.setData(List.of(Map.of("type", "bar", "y", List.of(5))));

        GraphCategoryPoint updatedPoint = graph.getGraphTraces().getFirst().getCategoryPoints().getFirst();
        assertSame(point, updatedPoint);
        assertEquals(java.math.BigDecimal.valueOf(5), updatedPoint.getValueNumeric());
        assertEquals(0, updatedPoint.getPointOrder());
    }

    @Test
    void emptyRelationalPayloadProducesEmptyData() {
        Graph graph = new Graph();
        graph.setData(List.of());

        assertTrue(GraphPayloadMapper.toTraceList(graph.getData()).isEmpty());
    }

    @Test
    void setDataCanonicalizesStaleGraphType() {
        Graph graph = new Graph();
        graph.setGraphType("line");

        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Trace 1",
            "x", List.of("2026-01-01T00:00:00Z"),
            "y", List.of(10)
        )));

        assertEquals("scatter", graph.getGraphType());
    }

    @Test
    void setDataNormalizesBarTracesToHorizontalOrientation() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Sessions",
            "x", List.of("Jan", "Feb"),
            "y", List.of(5, 7)
        )));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals("h", traces.getFirst().get("orientation"));
        assertEquals(List.of(5L, 7L), traces.getFirst().get("x"));
        assertEquals(List.of("Jan", "Feb"), traces.getFirst().get("y"));
    }

    @Test
    void setDataPreservesVerticalBarOrientation() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Sessions",
            "orientation", "v",
            "x", List.of("Jan", "Feb"),
            "y", List.of(5, 7)
        )));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals("v", traces.getFirst().get("orientation"));
        assertEquals(List.of("Jan", "Feb"), traces.getFirst().get("x"));
        assertEquals(List.of(5L, 7L), traces.getFirst().get("y"));
    }

    @Test
    void setDataDoesNotInventBarMarkerColorsWhenNoneAreProvided() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Sessions",
            "orientation", "v",
            "x", List.of("Jan", "Feb"),
            "y", List.of(5, 7)
        )));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertFalse(traces.getFirst().containsKey("marker"));
    }

    @Test
    void setDataRoundTripsPerBarMarkerColors() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Sessions",
            "orientation", "v",
            "x", List.of("Jan", "Feb"),
            "y", List.of(5, 7),
            "marker", Map.of(
                "color", "#1f77b4",
                "colors", List.of("#1f77b4", "#d62728")
            )
        )));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("bar", traces.getFirst().get("type"));
        assertEquals("v", traces.getFirst().get("orientation"));
        assertEquals(List.of("Jan", "Feb"), traces.getFirst().get("x"));
        assertEquals(List.of(5L, 7L), traces.getFirst().get("y"));
        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>) traces.getFirst().get("marker");
        assertEquals("#1f77b4", marker.get("color"));
        assertEquals(List.of("#1f77b4", "#d62728"), marker.get("colors"));
    }
}
