package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphRelationalPayloadMapperTest {
    @Test
    void setDataSyncsRelationalTracesAndKeepsTemplateSnapshot() {
        Graph graph = new Graph();
        graph.setTemplateData(List.of(Map.of("type", "scatter", "name", "Template")));
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Live",
            "x", List.of("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"),
            "y", List.of(10, 20)
        )));

        assertEquals(1, graph.getDataModelVersion());
        assertEquals("scatter", graph.getGraphType());
        assertEquals(List.of(Map.of("type", "scatter", "name", "Template")), graph.getTemplateData());

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
    void getDataReturnsLegacyTemplateDataWhenModelVersionIsMissing() throws Exception {
        Graph graph = new Graph();
        setRawTemplateData(graph, "{\"type\":\"pie\",\"labels\":[\"A\"],\"values\":[3]}");

        Object data = graph.getData();
        assertInstanceOf(String.class, data);
        assertEquals("{\"type\":\"pie\",\"labels\":[\"A\"],\"values\":[3]}", data);
    }

    @Test
    void emptyRelationalPayloadDoesNotFallBackToTemplateSnapshot() {
        Graph graph = new Graph();
        graph.setTemplateData(List.of(Map.of("type", "pie", "labels", List.of("A"), "values", List.of(3))));
        graph.setData(List.of());

        assertEquals(1, graph.getDataModelVersion());
        assertTrue(GraphPayloadMapper.toTraceList(graph.getData()).isEmpty());
    }

    private void setRawTemplateData(Graph graph, Object rawData) throws Exception {
        Field field = Graph.class.getDeclaredField("data");
        field.setAccessible(true);
        field.set(graph, rawData);
    }
}
