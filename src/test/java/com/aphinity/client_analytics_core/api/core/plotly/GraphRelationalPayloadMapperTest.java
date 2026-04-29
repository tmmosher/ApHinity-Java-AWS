package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphCategoryPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphRelationalPayloadMapperTest {
    @Test
    void setDataSyncsRelationalTracesAndClearsLegacyTemplateSnapshot() throws Exception {
        Graph graph = new Graph();
        setRawTemplateData(graph, List.of(Map.of("type", "scatter", "name", "Template")));
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Live",
            "x", List.of("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"),
            "y", List.of(10, 20)
        )));

        assertEquals(1, graph.getDataModelVersion());
        assertEquals("scatter", graph.getGraphType());
        assertEquals(null, readRawTemplateData(graph));

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
    void getDataReturnsEmptyListWhenOnlyLegacyTemplateSnapshotExists() throws Exception {
        Graph graph = new Graph();
        setRawTemplateData(graph, "{\"type\":\"pie\",\"labels\":[\"A\"],\"values\":[3]}");

        Object data = graph.getData();
        assertInstanceOf(List.class, data);
        assertTrue(GraphPayloadMapper.toTraceList(data).isEmpty());
    }

    @Test
    void setDataClearsStringBackedLegacyTemplateSnapshot() throws Exception {
        Graph graph = new Graph();
        setRawTemplateData(graph, "{\"type\":\"pie\",\"labels\":[\"A\"],\"values\":[3]}");

        graph.setData(List.of(Map.of("type", "pie", "labels", List.of("A"), "values", List.of(4))));

        assertEquals(null, readRawTemplateData(graph));
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(graph.getData());
        assertEquals(1, traces.size());
        assertEquals("pie", traces.getFirst().get("type"));
        assertEquals(List.of(4L), traces.getFirst().get("values"));
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
        graph.setDataModelVersion(1);

        graph.setData(List.of(Map.of("type", "bar", "y", List.of(5))));

        GraphCategoryPoint updatedPoint = graph.getGraphTraces().getFirst().getCategoryPoints().getFirst();
        assertSame(point, updatedPoint);
        assertEquals(java.math.BigDecimal.valueOf(5), updatedPoint.getValueNumeric());
        assertEquals(0, updatedPoint.getPointOrder());
    }

    @Test
    void emptyRelationalPayloadDoesNotFallBackToTemplateSnapshot() {
        Graph graph = new Graph();
        graph.setData(List.of());

        assertEquals(1, graph.getDataModelVersion());
        assertTrue(GraphPayloadMapper.toTraceList(graph.getData()).isEmpty());
    }

    @Test
    void setDataCanonicalizesStaleGraphMetadata() {
        Graph graph = new Graph();
        graph.setGraphType("line");
        graph.setDataModelVersion(0);

        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "Trace 1",
            "x", List.of("2026-01-01T00:00:00Z"),
            "y", List.of(10)
        )));

        assertEquals("scatter", graph.getGraphType());
        assertEquals(1, graph.getDataModelVersion());
    }

    private void setRawTemplateData(Graph graph, Object rawData) throws Exception {
        Field field = Graph.class.getDeclaredField("templateData");
        field.setAccessible(true);
        field.set(graph, rawData);
    }

    private Object readRawTemplateData(Graph graph) throws Exception {
        Field field = Graph.class.getDeclaredField("templateData");
        field.setAccessible(true);
        return field.get(graph);
    }
}
