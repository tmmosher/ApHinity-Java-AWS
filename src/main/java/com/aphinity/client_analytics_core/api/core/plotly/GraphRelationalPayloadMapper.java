package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphCategoryPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps Plotly payloads in sync with the relational trace/point tables.
 */
public final class GraphRelationalPayloadMapper {
    private static final String INTERNAL_X_FIELD = "x";
    private static final String INTERNAL_LABEL_FIELD = "label";

    private GraphRelationalPayloadMapper() {
    }

    public static void syncGraphData(Graph graph, List<Map<String, Object>> traces) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph is required");
        }

        List<GraphTrace> graphTraces = graph.getGraphTraces();
        if (graphTraces == null) {
            graphTraces = new ArrayList<>();
            graph.setGraphTraces(graphTraces);
        }

        if (traces == null || traces.isEmpty()) {
            graphTraces.clear();
            return;
        }

        graph.setGraphType(resolveCanonicalTraceType(traces.getFirst()));

        int sharedCount = Math.min(graphTraces.size(), traces.size());
        for (int index = 0; index < sharedCount; index++) {
            updateGraphTrace(graph, graphTraces.get(index), traces.get(index), index);
        }

        for (int index = sharedCount; index < traces.size(); index++) {
            graphTraces.add(buildGraphTrace(graph, traces.get(index), index));
        }

        while (graphTraces.size() > traces.size()) {
            graphTraces.remove(graphTraces.size() - 1);
        }
    }

    public static GraphPayloadMapper.GraphPayload normalize(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph is required");
        }

        List<GraphTrace> graphTraces = graph.getGraphTraces();
        if (graphTraces == null || graphTraces.isEmpty()) {
            return new GraphPayloadMapper.GraphPayload(
                List.of(),
                graph.getLayout(),
                graph.getConfig(),
                graph.getStyle()
            );
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        for (GraphTrace graphTrace : graphTraces) {
            traces.add(toTraceMap(graphTrace));
        }

        return new GraphPayloadMapper.GraphPayload(
            List.copyOf(traces),
            graph.getLayout(),
            graph.getConfig(),
            graph.getStyle()
        );
    }

    public static List<Map<String, Object>> toTraceList(Graph graph) {
        return normalize(graph).data();
    }

    private static GraphTrace buildGraphTrace(Graph graph, Map<String, Object> trace, int index) {
        GraphTrace graphTrace = new GraphTrace();
        populateGraphTrace(graph, graphTrace, trace, index, false);
        return graphTrace;
    }

    private static void updateGraphTrace(Graph graph, GraphTrace graphTrace, Map<String, Object> trace, int index) {
        populateGraphTrace(graph, graphTrace, trace, index, true);
    }

    private static void populateGraphTrace(
        Graph graph,
        GraphTrace graphTrace,
        Map<String, Object> trace,
        int index,
        boolean preserveExistingTraceKey
    ) {
        String canonicalType = resolveCanonicalTraceType(trace);
        boolean timeSeriesTrace = "scatter".equals(canonicalType) && looksLikeTimeSeries(trace);
        graphTrace.setGraph(graph);
        if (!preserveExistingTraceKey || graphTrace.getTraceKey() == null || graphTrace.getTraceKey().isBlank()) {
            graphTrace.setTraceKey(canonicalType + "-" + (index + 1));
        }
        graphTrace.setTraceName(resolveTraceName(trace, index));
        graphTrace.setTraceType(canonicalType);
        graphTrace.setTraceOrder(index);
        graphTrace.setDataMode(resolveDataMode(canonicalType, trace));
        graphTrace.setTraceConfig(extractTraceConfig(trace));

        ensureCategoryPoints(graphTrace);
        ensureTimeSeriesPoints(graphTrace);

        if (timeSeriesTrace) {
            graphTrace.getCategoryPoints().clear();
            populateTimeSeriesPoints(graphTrace, trace);
            return;
        }

        graphTrace.getTimeSeriesPoints().clear();
        switch (canonicalType) {
            case "pie" -> populatePiePoints(graphTrace, trace);
            case "indicator" -> populateIndicatorPoint(graphTrace, trace);
            case "bar" -> populateCartesianPoints(graphTrace, trace);
            case "scatter" -> populateCartesianPoints(graphTrace, trace);
            default -> throw new IllegalArgumentException("Graph data is invalid");
        }
    }

    private static Map<String, Object> toTraceMap(GraphTrace trace) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (trace.getTraceConfig() != null) {
            result.putAll(normalizeTraceConfig(trace.getTraceConfig()));
        }
        result.put("type", trace.getTraceType());
        result.put("name", trace.getTraceName());

        switch (trace.getTraceType()) {
            case "pie" -> {
                List<Object> labels = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                for (GraphCategoryPoint point : trace.getCategoryPoints()) {
                    labels.add(resolveLabelValue(point));
                    values.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                }
                result.put("labels", List.copyOf(labels));
                result.put("values", List.copyOf(values));
            }
            case "indicator" -> {
                GraphCategoryPoint point = trace.getCategoryPoints().isEmpty() ? null : trace.getCategoryPoints().getFirst();
                Number value = point == null
                    ? 0L
                    : resolveNumericValue(point.getValueNumeric(), point.getValueText());
                result.put("value", value);
            }
            case "bar", "scatter" -> {
                List<Object> xValues = new ArrayList<>();
                List<Object> yValues = new ArrayList<>();

                if ("scatter".equals(trace.getTraceType()) && "time_series".equals(trace.getDataMode())) {
                    for (GraphTimeSeriesPoint point : trace.getTimeSeriesPoints()) {
                        xValues.add(resolveXValue(point.getPointMeta(), point.getObservedAt()));
                        yValues.add(resolveNumericValue(point.getYNumeric(), point.getYText()));
                    }
                } else {
                    for (GraphCategoryPoint point : trace.getCategoryPoints()) {
                        xValues.add(resolveXValue(point.getPointMeta(), point.getPointOrder()));
                        yValues.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                    }
                }

                result.put("x", List.copyOf(xValues));
                result.put("y", List.copyOf(yValues));
            }
            default -> throw new IllegalArgumentException("Graph data is invalid");
        }

        return result;
    }

    private static void populatePiePoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> labels = optionalList(trace, "labels");
        if (labels == null) {
            labels = List.of();
        }
        List<?> values = optionalList(trace, "values");
        if (values == null) {
            values = List.of();
        }
        if (labels.size() != values.size()) {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        for (int index = 0; index < labels.size(); index++) {
            GraphCategoryPoint point = categoryPointAt(graphTrace, index);
            Object rawLabel = labels.get(index);
            if (!(rawLabel instanceof String label)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }
            Object rawValue = values.get(index);
            if (!(rawValue instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }

            point.setCategoryKey(label);
            point.setCategoryLabel(label);
            point.setPointOrder(index);
            point.setValueNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_LABEL_FIELD, label));
        }
        trimPoints(graphTrace.getCategoryPoints(), labels.size());
    }

    private static void populateIndicatorPoint(GraphTrace graphTrace, Map<String, Object> trace) {
        Object rawValue = trace.get("value");
        Number number;
        if (rawValue == null) {
            number = 0L;
        } else if (rawValue instanceof Number numericValue) {
            number = numericValue;
        } else {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        GraphCategoryPoint point = categoryPointAt(graphTrace, 0);
        point.setCategoryKey("current");
        point.setCategoryLabel(resolveTraceName(trace, 0));
        point.setPointOrder(0);
        point.setValueNumeric(toBigDecimal(number));
        point.setPointMeta(Map.of());
        trimPoints(graphTrace.getCategoryPoints(), 1);
    }

    private static void populateCartesianPoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> yValues = optionalList(trace, "y");
        if (yValues == null) {
            yValues = List.of();
        }
        List<?> xValues = optionalList(trace, "x");
        boolean hasExplicitX = xValues != null && !xValues.isEmpty();

        if (hasExplicitX && yValues.isEmpty()) {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        if (hasExplicitX && looksLikeTimeSeries(trace)) {
            graphTrace.getCategoryPoints().clear();
            populateTimeSeriesPoints(graphTrace, trace);
            return;
        }

        for (int index = 0; index < yValues.size(); index++) {
            GraphCategoryPoint point = categoryPointAt(graphTrace, index);
            Object rawY = yValues.get(index);
            if (!(rawY instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }

            Object rawX = hasExplicitX && index < xValues.size() ? xValues.get(index) : index;
            point.setCategoryKey(String.valueOf(rawX));
            point.setCategoryLabel(rawX instanceof String ? (String) rawX : String.valueOf(rawX));
            point.setPointOrder(index);
            point.setValueNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_X_FIELD, rawX));
        }
        trimPoints(graphTrace.getCategoryPoints(), yValues.size());
    }

    private static void populateTimeSeriesPoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> yValues = requireList(trace, "y");
        List<?> xValues = optionalList(trace, "x");
        if (xValues == null) {
            xValues = List.of();
        }

        for (int index = 0; index < yValues.size(); index++) {
            GraphTimeSeriesPoint point = timeSeriesPointAt(graphTrace, index);
            Object rawY = yValues.get(index);
            if (!(rawY instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }
            Object rawX = index < xValues.size() ? xValues.get(index) : index;
            Instant observedAt = parseObservedAt(rawX);

            point.setObservedAt(observedAt);
            point.setPointOrder(index);
            point.setYNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_X_FIELD, rawX));
        }
        trimPoints(graphTrace.getTimeSeriesPoints(), yValues.size());
    }

    private static String resolveDataMode(String canonicalType, Map<String, Object> trace) {
        if (!"scatter".equals(canonicalType)) {
            return "categorical";
        }
        List<?> xValues = optionalList(trace, "x");
        if (xValues == null || xValues.isEmpty()) {
            return "categorical";
        }
        return looksLikeTimeSeries(trace) ? "time_series" : "categorical";
    }

    private static boolean looksLikeTimeSeries(Map<String, Object> trace) {
        List<?> xValues = optionalList(trace, "x");
        if (xValues == null || xValues.isEmpty()) {
            return false;
        }

        for (Object rawX : xValues) {
            if (parseObservedAt(rawX) == null) {
                return false;
            }
        }
        return true;
    }

    private static String resolveTraceName(Map<String, Object> trace, int index) {
        Object rawName = trace.get("name");
        if (rawName instanceof String name && !name.isBlank()) {
            return name;
        }
        return "Trace " + (index + 1);
    }

    private static String resolveCanonicalTraceType(Map<String, Object> trace) {
        Object rawType = trace.get("type");
        if (!(rawType instanceof String type)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }
        return switch (type.strip().toLowerCase(Locale.ROOT)) {
            case "pie" -> "pie";
            case "indicator" -> "indicator";
            case "bar" -> "bar";
            case "scatter", "scattergl", "line" -> "scatter";
            default -> throw new IllegalArgumentException("Graph data is invalid");
        };
    }

    private static Map<String, Object> normalizeTraceConfig(Map<String, Object> traceConfig) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        traceConfig.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key, normalizeJsonValue(value));
            }
        });
        return normalized;
    }

    private static Object normalizeJsonValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            mapValue.forEach((key, entry) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key), normalizeJsonValue(entry));
                }
            });
            return normalized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalized = new ArrayList<>(listValue.size());
            for (Object entry : listValue) {
                normalized.add(normalizeJsonValue(entry));
            }
            return normalized;
        }
        if (value instanceof Number number) {
            return normalizeNumber(number);
        }
        return value;
    }

    private static Number normalizeNumber(Number number) {
        try {
            BigDecimal normalized = number instanceof BigDecimal bigDecimal
                ? bigDecimal.stripTrailingZeros()
                : new BigDecimal(number.toString()).stripTrailingZeros();
            if (normalized.scale() <= 0) {
                return normalized.longValueExact();
            }
            return normalized;
        } catch (ArithmeticException | NumberFormatException ex) {
            return number;
        }
    }

    private static Map<String, Object> extractTraceConfig(Map<String, Object> trace) {
        Map<String, Object> config = new LinkedHashMap<>(trace);
        config.remove("type");
        config.remove("name");
        config.remove("x");
        config.remove("y");
        config.remove("labels");
        config.remove("values");
        config.remove("value");
        return config;
    }

    private static void ensureCategoryPoints(GraphTrace graphTrace) {
        if (graphTrace.getCategoryPoints() == null) {
            graphTrace.setCategoryPoints(new ArrayList<>());
        }
    }

    private static void ensureTimeSeriesPoints(GraphTrace graphTrace) {
        if (graphTrace.getTimeSeriesPoints() == null) {
            graphTrace.setTimeSeriesPoints(new ArrayList<>());
        }
    }

    private static GraphCategoryPoint categoryPointAt(GraphTrace graphTrace, int index) {
        List<GraphCategoryPoint> points = graphTrace.getCategoryPoints();
        while (points.size() <= index) {
            GraphCategoryPoint point = new GraphCategoryPoint();
            point.setGraphTrace(graphTrace);
            points.add(point);
        }
        GraphCategoryPoint point = points.get(index);
        point.setGraphTrace(graphTrace);
        return point;
    }

    private static GraphTimeSeriesPoint timeSeriesPointAt(GraphTrace graphTrace, int index) {
        List<GraphTimeSeriesPoint> points = graphTrace.getTimeSeriesPoints();
        while (points.size() <= index) {
            GraphTimeSeriesPoint point = new GraphTimeSeriesPoint();
            point.setGraphTrace(graphTrace);
            points.add(point);
        }
        GraphTimeSeriesPoint point = points.get(index);
        point.setGraphTrace(graphTrace);
        return point;
    }

    private static <T> void trimPoints(List<T> points, int desiredSize) {
        while (points.size() > desiredSize) {
            points.remove(points.size() - 1);
        }
    }

    private static List<?> requireList(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }
        return list;
    }

    private static List<?> optionalList(Map<String, Object> trace, String field) {
        Object value = trace.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }
        return list;
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(number.toString());
    }

    private static Number resolveNumericValue(BigDecimal numericValue, String textValue) {
        if (numericValue != null) {
            try {
                return numericValue.longValueExact();
            } catch (ArithmeticException ignored) {
                // fails and is ignored if the value is too large to fit in a long
                return numericValue.stripTrailingZeros();
            }
        }
        if (textValue != null) {
            try {
                return BigDecimal.valueOf(Double.parseDouble(textValue)).longValueExact();
            } catch (ArithmeticException ignored) {
                return BigDecimal.valueOf(Double.parseDouble(textValue)).stripTrailingZeros();
            } catch (NumberFormatException e) {
                // not really recoverable, return error state
                return 0L;
            }
        }
        return 0L;
    }

    private static Object resolveXValue(Map<String, Object> pointMeta, Object fallback) {
        if (pointMeta != null && pointMeta.containsKey(INTERNAL_X_FIELD)) {
            return pointMeta.get(INTERNAL_X_FIELD);
        }
        return fallback;
    }

    private static Object resolveLabelValue(GraphCategoryPoint point) {
        if (point.getPointMeta() != null && point.getPointMeta().containsKey(INTERNAL_LABEL_FIELD)) {
            return point.getPointMeta().get(INTERNAL_LABEL_FIELD);
        }
        if (point.getCategoryLabel() != null) {
            return point.getCategoryLabel();
        }
        return point.getCategoryKey();
    }

    private static Instant parseObservedAt(Object rawX) {
        if (rawX instanceof Instant instant) {
            return instant;
        }
        if (rawX instanceof CharSequence text) {
            String value = text.toString().strip();
            if (value.isEmpty()) {
                return null;
            }
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
