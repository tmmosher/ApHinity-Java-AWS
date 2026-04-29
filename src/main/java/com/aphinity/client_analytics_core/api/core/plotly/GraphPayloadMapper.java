package com.aphinity.client_analytics_core.api.core.plotly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes the current Plotly trace payloads exchanged between the API and the relational graph model.
 */
public final class GraphPayloadMapper {
    private GraphPayloadMapper() {
    }

    public static GraphPayload normalize(
        Object rawData,
        Object rawLayout,
        Object rawConfig,
        Object rawStyle
    ) {
        return new GraphPayload(
            toTraceList(rawData),
            asObjectMap(rawLayout),
            asObjectMap(rawConfig),
            asObjectMap(rawStyle)
        );
    }

    public static List<Map<String, Object>> toTraceList(Object rawData) {
        return switch (rawData) {
            case null -> List.of();
            case List<?> traceList -> copyTraceList(traceList);
            case Map<?, ?> traceMap -> {
                Map<String, Object> objectData = copyObjectMap(traceMap);
                if (objectData.isEmpty()) {
                    yield List.of();
                }
                if (isLegacySnapshotPayload(objectData)) {
                    throw new IllegalArgumentException("Graph data must be a trace object or trace array");
                }
                yield List.of(objectData);
            }
            default -> throw new IllegalArgumentException("Graph data must be a trace object or trace array");
        };
    }

    private static boolean isLegacySnapshotPayload(Map<String, Object> payload) {
        return payload.containsKey("data") && (
            payload.containsKey("layout") ||
                payload.containsKey("config") ||
                payload.containsKey("style")
        );
    }

    private static List<Map<String, Object>> copyTraceList(List<?> input) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> traces = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            Object entry = input.get(index);
            if (entry instanceof Map<?, ?> mapEntry) {
                traces.add(copyObjectMap(mapEntry));
                continue;
            }
            throw new IllegalArgumentException("Graph trace at index " + index + " must be an object");
        }
        return List.copyOf(traces);
    }

    private static Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return copyObjectMap(map);
    }

    private static Map<String, Object> copyObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            copy.put(String.valueOf(key), value);
        });
        return copy;
    }

    public record GraphPayload(
        List<Map<String, Object>> data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style
    ) {
    }
}
