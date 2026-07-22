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
    void setDataRoundTripsTableTraceRows() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "table",
            "name", "Summary",
            "header", Map.of(
                "values", List.of("Metric", "Value"),
                "align", "left"
            ),
            "cells", Map.of(
                "values", List.of(
                    List.of("Open", "Closed"),
                    List.of(3, 7)
                ),
                "align", "left"
            )
        )));

        assertEquals("table", graph.getGraphType());
        GraphTrace trace = graph.getGraphTraces().getFirst();
        assertEquals("table", trace.getTraceType());
        assertEquals("table", trace.getDataMode());
        assertEquals(2, trace.getCategoryPoints().size());
        assertEquals(List.of("Open", 3L), trace.getCategoryPoints().getFirst().getPointMeta().get("values"));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("table", traces.getFirst().get("type"));
        assertEquals(Map.of(
            "values", List.of("Metric", "Value"),
            "align", "left"
        ), traces.getFirst().get("header"));
        assertEquals(Map.of(
            "values", List.of(
                List.of("Open", "Closed"),
                List.of(3L, 7L)
            ),
            "align", "left"
        ), traces.getFirst().get("cells"));
    }

    @Test
    void setDataRoundTripsSunburstNodes() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "sunburst",
            "name", "Conformance",
            "ids", List.of("site", "site/asset"),
            "labels", List.of("Site", "Asset"),
            "parents", List.of("", "site"),
            "values", List.of(10, 4),
            "branchvalues", "total",
            "marker", Map.of("colors", List.of("#1f77b4", "#16a34a"))
        )));

        assertEquals("sunburst", graph.getGraphType());
        GraphTrace trace = graph.getGraphTraces().getFirst();
        assertEquals("sunburst", trace.getTraceType());
        assertEquals("categorical", trace.getDataMode());
        assertEquals(2, trace.getCategoryPoints().size());
        assertEquals("site/asset", trace.getCategoryPoints().get(1).getCategoryKey());
        assertEquals("site", trace.getCategoryPoints().get(1).getPointMeta().get("parent"));
        assertEquals("#16a34a", trace.getCategoryPoints().get(1).getPointMeta().get("color"));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        Map<String, Object> result = traces.getFirst();
        assertEquals(List.of("site", "site/asset"), result.get("ids"));
        assertEquals(List.of("Site", "Asset"), result.get("labels"));
        assertEquals(List.of("", "site"), result.get("parents"));
        assertEquals(List.of(10L, 4L), result.get("values"));
        assertEquals("total", result.get("branchvalues"));
        assertEquals(Map.of("colors", List.of("#1f77b4", "#16a34a")), result.get("marker"));
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
        assertEquals(List.of("#1f77b4", "#d62728"), marker.get("color"));
        assertEquals(List.of("#1f77b4", "#d62728"), marker.get("colors"));
    }

    @Test
    void setDataRoundTripsPlotlyBarMarkerColorArray() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of(
            "type", "bar",
            "name", "Sessions",
            "orientation", "v",
            "x", List.of("Jan", "Feb"),
            "y", List.of(5, 7),
            "marker", Map.of("color", List.of("#1f77b4", "#d62728"))
        )));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        @SuppressWarnings("unchecked")
        Map<String, Object> marker = (Map<String, Object>) traces.getFirst().get("marker");
        assertEquals(List.of("#1f77b4", "#d62728"), marker.get("color"));
        assertEquals(List.of("#1f77b4", "#d62728"), marker.get("colors"));
    }
}
