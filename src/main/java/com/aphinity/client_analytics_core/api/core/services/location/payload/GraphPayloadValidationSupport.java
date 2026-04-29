package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GraphPayloadValidationSupport {
    private GraphPayloadValidationSupport() {
    }

    static IllegalArgumentException invalidGraphData() {
        return new IllegalArgumentException("Graph data is invalid");
    }

    static GraphPayloadFamily resolveFamily(String traceType) {
        if (traceType == null || traceType.isBlank()) {
            throw invalidGraphData();
        }

        return switch (traceType.strip().toLowerCase(Locale.ROOT)) {
            case "pie" -> GraphPayloadFamily.PIE;
            case "indicator" -> GraphPayloadFamily.INDICATOR;
            case "bar", "scatter", "scattergl", "line" -> GraphPayloadFamily.CARTESIAN;
            default -> throw invalidGraphData();
        };
    }

    static String resolveCanonicalTraceType(String traceType) {
        if (traceType == null || traceType.isBlank()) {
            throw invalidGraphData();
        }

        return switch (traceType.strip().toLowerCase(Locale.ROOT)) {
            case "pie" -> "pie";
            case "indicator" -> "indicator";
            case "bar" -> "bar";
            case "scatter", "scattergl", "line" -> "scatter";
            default -> throw invalidGraphData();
        };
    }

    static String resolveCanonicalTraceType(Map<String, Object> trace) {
        Object rawType = trace.get("type");
        if (!(rawType instanceof String traceType)) {
            throw invalidGraphData();
        }
        return resolveCanonicalTraceType(traceType);
    }

    static String resolveExpectedCanonicalType(List<Map<String, Object>> traces) {
        if (traces.isEmpty()) {
            return null;
        }
        return resolveCanonicalTraceType(traces.getFirst());
    }

    static void requireCanonicalTraceType(
        Map<String, Object> trace,
        String expectedCanonicalType
    ) {
        if (!expectedCanonicalType.equals(resolveCanonicalTraceType(trace))) {
            throw invalidGraphData();
        }
    }

    static void requireFamily(
        Map<String, Object> trace,
        GraphPayloadFamily expectedFamily
    ) {
        if (resolveFamily(resolveCanonicalTraceType(trace)) != expectedFamily) {
            throw invalidGraphData();
        }
    }

    static Map<String, Object> requireObjectField(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (!(value instanceof Map<?, ?> map)) {
            throw invalidGraphData();
        }
        return copyObjectMap(map);
    }

    static void requireStringList(List<?> values) {
        for (Object value : values) {
            if (!(value instanceof String)) {
                throw invalidGraphData();
            }
        }
    }

    static void requireNumericList(List<?> values) {
        for (Object value : values) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                throw invalidGraphData();
            }
        }
    }

    static void requireScalarList(List<?> values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                continue;
            }
            throw invalidGraphData();
        }
    }

    static List<?> requireListField(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (!(value instanceof List<?> list)) {
            throw invalidGraphData();
        }
        return list;
    }

    static String requireStringField(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidGraphData();
        }
        return text;
    }

    static Number requireNumberField(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw invalidGraphData();
        }
        return number;
    }

    static Map<String, Object> normalizeLayout(Object rawLayout) {
        if (rawLayout == null) {
            return null;
        }
        if (!(rawLayout instanceof Map<?, ?> layoutMap)) {
            throw invalidGraphData();
        }
        return copyObjectMap(layoutMap);
    }

    static Map<String, Object> copyObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }

    static boolean numericValueEquals(Number value, double expected) {
        return Double.compare(value.doubleValue(), expected) == 0;
    }
}
