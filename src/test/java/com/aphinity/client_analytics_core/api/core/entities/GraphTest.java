package com.aphinity.client_analytics_core.api.core.entities;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {
    @Test
    void setDataNormalizesMultipleTracesToArrayStorage() {
        Graph graph = new Graph();
        graph.setData(List.of(
            Map.of("type", "line", "name", "baseline"),
            Map.of("type", "bar", "name", "actual")
        ));

        Object storedData = graph.getData();
        assertInstanceOf(List.class, storedData);
        List<?> traces = (List<?>) storedData;
        assertEquals(2, traces.size());
    }

    @Test
    void setDataNormalizesSingleTraceToObjectStorage() {
        Graph graph = new Graph();
        graph.setData(List.of(Map.of("type", "pie", "name", "share")));

        Object storedData = graph.getData();
        assertInstanceOf(Map.class, storedData);
        assertEquals("pie", ((Map<?, ?>) storedData).get("type"));
    }

    @Test
    void setDataRejectsMalformedTraceArrays() {
        Graph graph = new Graph();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> graph.setData(List.of(Map.of("type", "bar"), "bad"))
        );

        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void setDataBackfillsLegacyNestedColumns() {
        Graph graph = new Graph();
        graph.setData(Map.of(
            "data", List.of(Map.of("type", "pie", "name", "share")),
            "layout", Map.of("showlegend", false),
            "config", Map.of("displayModeBar", false),
            "style", Map.of("height", 240)
        ));

        assertInstanceOf(Map.class, graph.getData());
        assertEquals("pie", ((Map<?, ?>) graph.getData()).get("type"));
        assertEquals(Map.of("showlegend", false), graph.getLayout());
        assertEquals(Map.of("displayModeBar", false), graph.getConfig());
        assertEquals(Map.of("height", 240), graph.getStyle());
    }
}
