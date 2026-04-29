package com.aphinity.client_analytics_core.api.core.plotly;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void normalizeAcceptsTraceArrays() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            List.of(
                Map.of("type", "bar", "name", "Sessions"),
                Map.of("type", "scatter", "name", "Trend")
            ),
            Map.of("showlegend", true),
            Map.of("displayModeBar", true),
            Map.of("height", 360)
        );

        assertEquals(2, payload.data().size());
        assertEquals("bar", payload.data().getFirst().get("type"));
        assertEquals(Map.of("showlegend", true), payload.layout());
        assertEquals(Map.of("displayModeBar", true), payload.config());
        assertEquals(Map.of("height", 360), payload.style());
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
    void normalizeRejectsLegacySnapshotPayload() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> GraphPayloadMapper.normalize(
                Map.of(
                    "data", List.of(Map.of("type", "bar")),
                    "layout", Map.of("showlegend", false)
                ),
                null,
                null,
                null
            )
        );

        assertTrue(ex.getMessage().contains("trace object or trace array"));
    }

    @Test
    void normalizeTreatsNonObjectColumnsAsNull() {
        GraphPayloadMapper.GraphPayload payload = GraphPayloadMapper.normalize(
            Map.of("type", "bar", "y", List.of(1, 2, 3)),
            List.of(),
            true,
            42
        );

        assertNull(payload.layout());
        assertNull(payload.config());
        assertNull(payload.style());
    }
}
