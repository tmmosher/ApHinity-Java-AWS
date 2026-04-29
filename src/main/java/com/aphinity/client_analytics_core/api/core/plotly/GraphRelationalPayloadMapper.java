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

import org.hibernate.Hibernate;

/**
 * Keeps Plotly payloads in sync with the relational trace/point tables.
 */
public final class GraphRelationalPayloadMapper {
    private static final int CURRENT_DATA_MODEL_VERSION = 1;
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
        } else {
            graphTraces.clear();
        }

        if (traces == null || traces.isEmpty()) {
            if (graph.getDataModelVersion() == null) {
                graph.setDataModelVersion(CURRENT_DATA_MODEL_VERSION);
            }
            return;
        }

        String graphType = graph.getGraphType();
        if (graphType == null || graphType.isBlank()) {
            graph.setGraphType(resolveCanonicalTraceType(traces.getFirst()));
        }
        if (graph.getDataModelVersion() == null) {
            graph.setDataModelVersion(CURRENT_DATA_MODEL_VERSION);
        }

        for (int index = 0; index < traces.size(); index++) {
            Map<String, Object> trace = traces.get(index);
            GraphTrace graphTrace = buildGraphTrace(graph, trace, index);
            graphTraces.add(graphTrace);
        }
    }

    public static GraphPayloadMapper.GraphPayload normalize(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph is required");
        }

        if (!isRelationalGraph(graph)) {
            return GraphPayloadMapper.normalize(
                graph.getTemplateData(),
                graph.getLayout(),
                graph.getConfig(),
                graph.getStyle()
            );
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        for (GraphTrace graphTrace : graph.getGraphTraces()) {
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

    public static String inferGraphType(Object rawData) {
        try {
            List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(rawData);
            if (traces.isEmpty()) {
                return null;
            }
            return resolveCanonicalTraceType(traces.getFirst());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static GraphTrace buildGraphTrace(Graph graph, Map<String, Object> trace, int index) {
        String canonicalType = resolveCanonicalTraceType(trace);
        GraphTrace graphTrace = new GraphTrace();
        graphTrace.setGraph(graph);
        graphTrace.setTraceKey(canonicalType + "-" + (index + 1));
        graphTrace.setTraceName(resolveTraceName(trace, index));
        graphTrace.setTraceType(canonicalType);
        graphTrace.setTraceOrder(index);
        graphTrace.setDataMode(resolveDataMode(canonicalType, trace));
        graphTrace.setTraceConfig(extractTraceConfig(trace));

        switch (canonicalType) {
            case "pie" -> populatePiePoints(graphTrace, trace);
            case "indicator" -> populateIndicatorPoint(graphTrace, trace);
            case "bar" -> populateCartesianPoints(graphTrace, trace);
            case "scatter" -> {
                if (looksLikeTimeSeries(trace)) {
                    populateTimeSeriesPoints(graphTrace, trace);
                } else {
                    populateCartesianPoints(graphTrace, trace);
                }
            }
            default -> throw new IllegalArgumentException("Graph data is invalid");
        }

        return graphTrace;
    }

    private static Map<String, Object> toTraceMap(GraphTrace trace) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (trace.getTraceConfig() != null) {
            result.putAll(trace.getTraceConfig());
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
                    ? 0
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
        List<?> labels = requireList(trace, "labels");
        List<?> values = requireList(trace, "values");
        if (labels.size() != values.size()) {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        for (int index = 0; index < labels.size(); index++) {
            Object rawLabel = labels.get(index);
            if (!(rawLabel instanceof String label)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }
            Object rawValue = values.get(index);
            if (!(rawValue instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }

            GraphCategoryPoint point = new GraphCategoryPoint();
            point.setGraphTrace(graphTrace);
            point.setCategoryKey(label);
            point.setCategoryLabel(label);
            point.setPointOrder(index);
            point.setValueNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_LABEL_FIELD, label));
            graphTrace.getCategoryPoints().add(point);
        }
    }

    private static void populateIndicatorPoint(GraphTrace graphTrace, Map<String, Object> trace) {
        Object rawValue = trace.get("value");
        if (!(rawValue instanceof Number number)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        GraphCategoryPoint point = new GraphCategoryPoint();
        point.setGraphTrace(graphTrace);
        point.setCategoryKey("current");
        point.setCategoryLabel(resolveTraceName(trace, 0));
        point.setPointOrder(0);
        point.setValueNumeric(toBigDecimal(number));
        point.setPointMeta(Map.of());
        graphTrace.getCategoryPoints().add(point);
    }

    private static void populateCartesianPoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> yValues = requireList(trace, "y");
        List<?> xValues = optionalList(trace, "x");
        boolean hasExplicitX = xValues != null;

        if (hasExplicitX && !xValues.isEmpty() && looksLikeTimeSeries(trace)) {
            populateTimeSeriesPoints(graphTrace, trace);
            return;
        }

        for (int index = 0; index < yValues.size(); index++) {
            Object rawY = yValues.get(index);
            if (!(rawY instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }

            Object rawX = hasExplicitX && index < xValues.size() ? xValues.get(index) : index;
            GraphCategoryPoint point = new GraphCategoryPoint();
            point.setGraphTrace(graphTrace);
            point.setCategoryKey(String.valueOf(rawX));
            point.setCategoryLabel(rawX instanceof String ? (String) rawX : String.valueOf(rawX));
            point.setPointOrder(index);
            point.setValueNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_X_FIELD, rawX));
            graphTrace.getCategoryPoints().add(point);
        }
    }

    private static void populateTimeSeriesPoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> yValues = requireList(trace, "y");
        List<?> xValues = optionalList(trace, "x");
        if (xValues == null) {
            xValues = List.of();
        }

        for (int index = 0; index < yValues.size(); index++) {
            Object rawY = yValues.get(index);
            if (!(rawY instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }
            Object rawX = index < xValues.size() ? xValues.get(index) : index;
            Instant observedAt = parseObservedAt(rawX);

            GraphTimeSeriesPoint point = new GraphTimeSeriesPoint();
            point.setGraphTrace(graphTrace);
            point.setObservedAt(observedAt);
            point.setPointOrder(index);
            point.setYNumeric(toBigDecimal(number));
            point.setPointMeta(Map.of(INTERNAL_X_FIELD, rawX));
            graphTrace.getTimeSeriesPoints().add(point);
        }
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
            case "scatter", "scattergl" -> "scatter";
            default -> throw new IllegalArgumentException("Graph data is invalid");
        };
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

    private static boolean isRelationalGraph(Graph graph) {
        Integer dataModelVersion = graph.getDataModelVersion();
        if (dataModelVersion != null && dataModelVersion >= CURRENT_DATA_MODEL_VERSION) {
            return true;
        }
        List<GraphTrace> graphTraces = graph.getGraphTraces();
        return graphTraces != null && Hibernate.isInitialized(graphTraces) && !graphTraces.isEmpty();
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
                return numericValue.stripTrailingZeros();
            }
        }
        if (textValue != null) {
            return textValue;
        }
        return 0;
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
