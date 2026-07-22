package com.aphinity.client_analytics_core.api.core.entities;

import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.readData;
import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.writeData;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {
    @Test
    void setDataNormalizesMultipleTracesToArrayStorage() {
        Graph graph = new Graph();
        writeData(graph, List.of(
            Map.of("type", "line", "name", "baseline"),
            Map.of("type", "bar", "name", "actual")
        ));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(readData(graph));
        assertEquals(2, traces.size());
    }

    @Test
    void setDataNormalizesSingleTraceToObjectStorage() {
        Graph graph = new Graph();
        writeData(graph, List.of(Map.of("type", "pie", "name", "share")));

        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(readData(graph));
        assertEquals(1, traces.size());
        assertEquals("pie", traces.getFirst().get("type"));
    }

    @Test
    void setDataRejectsMalformedTraceArrays() {
        Graph graph = new Graph();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> writeData(graph, List.of(Map.of("type", "bar"), "bad"))
        );

        assertTrue(ex.getMessage().contains("index 1"));
    }
}
