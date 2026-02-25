package com.aphinity.client_analytics_core.api.core.plotly;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphPayloadMapperTest {
    @Test
    void normalizeWrapsSingleTraceObject() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            Map.of("type", "pie", "labels", List.of("fill", "rest")),
            Map.of("showlegend", false),
            Map.of("displayModeBar", false),
            Map.of("height", 240)
        );

        assertEquals(1, payload.data().size());
        assertEquals("pie", payload.data().getFirst().get("type"));
        assertEquals(Map.of("showlegend", false), payload.layout());
        assertEquals(Map.of("displayModeBar", false), payload.config());
        assertEquals(Map.of("height", 240), payload.style());
    }

    @Test
    void normalizeExtractsLegacyColumnsWhenTopLevelColumnsMissing() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            Map.of(
                "data", List.of(Map.of("type", "bar", "name", "Sessions")),
                "layout", Map.of("showlegend", true),
                "config", Map.of("displayModeBar", true),
                "style", Map.of("height", 360)
            ),
            null,
            null,
            null
        );

        assertEquals(1, payload.data().size());
        assertEquals("bar", payload.data().getFirst().get("type"));
        assertEquals(Map.of("showlegend", true), payload.layout());
        assertEquals(Map.of("displayModeBar", true), payload.config());
        assertEquals(Map.of("height", 360), payload.style());
    }

    @Test
    void toStoredDataKeepsSingleTraceAsObjectAndMultipleAsArray() {
        Object single = GraphPayloadMapper.toStoredData(List.of(Map.of("type", "line")));
        assertInstanceOf(Map.class, single);
        assertEquals("line", ((Map<?, ?>) single).get("type"));

        Object multiple = GraphPayloadMapper.toStoredData(List.of(
            Map.of("type", "line"),
            Map.of("type", "bar")
        ));
        assertInstanceOf(List.class, multiple);
        List<?> traces = (List<?>) multiple;
        assertEquals(2, traces.size());
        assertTrue(traces.getFirst() instanceof Map);
    }

    @Test
    void toStoredDataPersistsEmptyTracesAsArray() {
        Object stored = GraphPayloadMapper.toStoredData(List.of());
        assertInstanceOf(List.class, stored);
        assertTrue(((List<?>) stored).isEmpty());
    }

    @Test
    void normalizeTreatsEmptyObjectAsNoTraces() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            Map.of(),
            null,
            null,
            null
        );

        assertTrue(payload.data().isEmpty());
        assertNull(payload.layout());
        assertNull(payload.config());
        assertNull(payload.style());
    }

    @Test
    void normalizeRejectsInvalidTraceArrays() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> GraphPayloadMapper.normalize(
                List.of(Map.of("type", "line"), "bad-entry"),
                null,
                null,
                null
            )
        );

        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void normalizeRejectsInvalidDataType() {
        assertThrows(
            IllegalArgumentException.class,
            () -> GraphPayloadMapper.normalize("invalid", null, null, null)
        );
    }

    @Test
    void normalizeAcceptsJsonTextDataAndColumns() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            "{\"type\":\"pie\",\"labels\":[\"fill\",\"rest\"],\"values\":[65,35]}",
            "{\"showlegend\":false}",
            "{}",
            "{}"
        );

        assertEquals(1, payload.data().size());
        assertEquals("pie", payload.data().getFirst().get("type"));
        assertEquals(List.of("fill", "rest"), payload.data().getFirst().get("labels"));
        assertEquals(List.of(65L, 35L), payload.data().getFirst().get("values"));
        assertEquals(Map.of("showlegend", false), payload.layout());
        assertEquals(Map.of(), payload.config());
        assertEquals(Map.of(), payload.style());
    }
}
