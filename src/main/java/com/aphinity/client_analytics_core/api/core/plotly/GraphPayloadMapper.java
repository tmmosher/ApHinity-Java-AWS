package com.aphinity.client_analytics_core.api.core.plotly;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes graph JSON columns between DB storage and API response payloads.
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
        Map<String, Object> layout = asObjectMap(rawLayout);
        Map<String, Object> config = asObjectMap(rawConfig);
        Map<String, Object> style = asObjectMap(rawStyle);
        List<Map<String, Object>> traces = toTraceList(rawData);

        if (rawData instanceof Map<?, ?> mapData) {
            Map<String, Object> objectData = copyObjectMap(mapData);
            if (isLegacyNestedPayload(objectData)) {
                if (layout == null) {
                    layout = asObjectMap(objectData.get("layout"));
                }
                if (config == null) {
                    config = asObjectMap(objectData.get("config"));
                }
                if (style == null) {
                    style = asObjectMap(objectData.get("style"));
                }
            }
        }

        return new GraphPayload(traces, layout, config, style);
    }

    /**
     * Converts API trace arrays back into DB-stored JSON.
     * Stores one trace as an object for backward compatibility with existing rows.
     */
    public static Object toStoredData(List<Map<String, Object>> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }
        if (traces.size() == 1) {
            return copyObjectMap(traces.getFirst());
        }

        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> trace : traces) {
            copy.add(copyObjectMap(trace));
        }
        return List.copyOf(copy);
    }

    public static List<Map<String, Object>> toTraceList(Object rawData) {
        switch (rawData) {
            case null -> {
                return List.of();
            }
            case String jsonText -> {
                Object decoded = decodeJsonText(jsonText, "data");
                return toTraceList(decoded);
            }
            case JsonValue jsonValue -> {
                return toTraceList(fromJsonValue(jsonValue));
            }
            case List<?> traceList -> {
                return copyTraceList(traceList);
            }
            case Map<?, ?> traceMap -> {
                Map<String, Object> objectData = copyObjectMap(traceMap);
                if (objectData.isEmpty()) {
                    return List.of();
                }
                if (isLegacyNestedPayload(objectData)) {
                    Object nestedData = objectData.get("data");
                    if (nestedData instanceof String nestedJsonText) {
                        return toTraceList(decodeJsonText(nestedJsonText, "data"));
                    }
                    if (nestedData instanceof JsonValue nestedJsonValue) {
                        return toTraceList(fromJsonValue(nestedJsonValue));
                    }
                    if (nestedData instanceof List<?> nestedList) {
                        return copyTraceList(nestedList);
                    }
                    if (nestedData instanceof Map<?, ?> nestedMap) {
                        return List.of(copyObjectMap(nestedMap));
                    }
                    if (nestedData == null) {
                        return List.of();
                    }
                    throw new IllegalArgumentException("Graph legacy data field must be an object or array");
                }
                return List.of(objectData);
            }
            default -> {
            }
        }
        throw new IllegalArgumentException("Graph data must be an object or array");
    }

    private static boolean isLegacyNestedPayload(Map<String, Object> payload) {
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
        if (value instanceof String jsonText) {
            Object decoded = decodeJsonText(jsonText, "object column");
            if (decoded instanceof Map<?, ?> decodedMap) {
                return copyObjectMap(decodedMap);
            }
            return null;
        }
        if (value instanceof JsonObject jsonObject) {
            return copyObjectMap(fromJsonObject(jsonObject));
        }
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

    private static Object decodeJsonText(String jsonText, String fieldName) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalArgumentException("Graph " + fieldName + " JSON text is empty");
        }
        try (JsonReader reader = Json.createReader(new StringReader(jsonText))) {
            return fromJsonValue(reader.readValue());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Graph " + fieldName + " JSON text is invalid", ex);
        }
    }

    private static Object fromJsonValue(JsonValue jsonValue) {
        return switch (jsonValue.getValueType()) {
            case OBJECT -> fromJsonObject(jsonValue.asJsonObject());
            case ARRAY -> fromJsonArray(jsonValue.asJsonArray());
            case STRING -> ((JsonString) jsonValue).getString();
            case NUMBER -> toJavaNumber((JsonNumber) jsonValue);
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static Map<String, Object> fromJsonObject(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        jsonObject.forEach((key, value) -> map.put(key, fromJsonValue(value)));
        return map;
    }

    private static List<Object> fromJsonArray(JsonArray jsonArray) {
        List<Object> list = new ArrayList<>(jsonArray.size());
        for (JsonValue value : jsonArray) {
            list.add(fromJsonValue(value));
        }
        return List.copyOf(list);
    }

    private static Number toJavaNumber(JsonNumber number) {
        if (number.isIntegral()) {
            try {
                return number.longValueExact();
            } catch (ArithmeticException ignored) {
                return number.bigIntegerValue();
            }
        }
        return number.bigDecimalValue();
    }

    public record GraphPayload(
        List<Map<String, Object>> data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style
    ) {
    }
}
