package com.aphinity.client_analytics_core.api.core.plotly;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphCategoryPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeRange;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps Plotly payloads in sync with the relational trace/point tables.
 */
public final class GraphRelationalPayloadMapper {
    private static final String INTERNAL_X_FIELD = "x";
    private static final String INTERNAL_LABEL_FIELD = "label";
    private static final String INTERNAL_CUSTOMDATA_FIELD = "customdata";
    private static final String INTERNAL_COLOR_FIELD = "color";
    private static final DateTimeFormatter FLEXIBLE_LOCAL_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    private GraphRelationalPayloadMapper() {
    }

    public static void syncGraphData(Graph graph, List<Map<String, Object>> traces) {
        syncGraphData(graph, traces, GraphTimeRange.ALL_TIME);
    }

    public static void syncGraphData(Graph graph, List<Map<String, Object>> traces, GraphTimeRange timeRange) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph is required");
        }
        GraphTimeRange normalizedTimeRange = timeRange == null ? GraphTimeRange.ALL_TIME : timeRange;

        List<GraphTrace> graphTraces = graph.getGraphTraces();
        if (graphTraces == null) {
            graphTraces = new ArrayList<>();
            graph.setGraphTraces(graphTraces);
        }

        List<GraphTrace> scopedGraphTraces = graphTraces.stream()
            .filter(graphTrace -> normalizedTimeRange == graphTrace.getTimeRange())
            .sorted(java.util.Comparator
                .comparingInt(GraphTrace::getTraceOrder)
                .thenComparing(GraphTrace::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();

        if (traces == null || traces.isEmpty()) {
            graphTraces.removeIf(graphTrace -> normalizedTimeRange == graphTrace.getTimeRange());
            return;
        }

        if (normalizedTimeRange == GraphTimeRange.ALL_TIME) {
            graph.setGraphType(resolveCanonicalTraceType(traces.getFirst()));
        }

        int sharedCount = Math.min(scopedGraphTraces.size(), traces.size());
        for (int index = 0; index < sharedCount; index++) {
            updateGraphTrace(graph, scopedGraphTraces.get(index), traces.get(index), index, normalizedTimeRange);
        }

        for (int index = sharedCount; index < traces.size(); index++) {
            graphTraces.add(buildGraphTrace(graph, traces.get(index), index, normalizedTimeRange));
        }

        for (int index = traces.size(); index < scopedGraphTraces.size(); index++) {
            graphTraces.remove(scopedGraphTraces.get(index));
        }
    }

    public static GraphPayloadMapper.GraphPayload normalize(Graph graph) {
        return normalize(graph, GraphTimeRange.ALL_TIME);
    }

    public static GraphPayloadMapper.GraphPayload normalize(Graph graph, GraphTimeRange timeRange) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph is required");
        }
        GraphTimeRange normalizedTimeRange = timeRange == null ? GraphTimeRange.ALL_TIME : timeRange;

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
            if (graphTrace == null || graphTrace.getTimeRange() != normalizedTimeRange) {
                continue;
            }
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

    private static GraphTrace buildGraphTrace(
        Graph graph,
        Map<String, Object> trace,
        int index,
        GraphTimeRange timeRange
    ) {
        GraphTrace graphTrace = new GraphTrace();
        populateGraphTrace(graph, graphTrace, trace, index, false, timeRange);
        return graphTrace;
    }

    private static void updateGraphTrace(
        Graph graph,
        GraphTrace graphTrace,
        Map<String, Object> trace,
        int index,
        GraphTimeRange timeRange
    ) {
        populateGraphTrace(graph, graphTrace, trace, index, true, timeRange);
    }

    private static void populateGraphTrace(
        Graph graph,
        GraphTrace graphTrace,
        Map<String, Object> trace,
        int index,
        boolean preserveExistingTraceKey,
        GraphTimeRange timeRange
    ) {
        String canonicalType = resolveCanonicalTraceType(trace);
        boolean timeSeriesTrace = "scatter".equals(canonicalType) && looksLikeTimeSeries(trace);
        graphTrace.setGraph(graph);
        graphTrace.setTimeRange(timeRange);
        if (!preserveExistingTraceKey || graphTrace.getTraceKey() == null || graphTrace.getTraceKey().isBlank()) {
            graphTrace.setTraceKey(canonicalType + "-" + (index + 1));
        }
        graphTrace.setTraceName(resolveTraceName(trace, index));
        graphTrace.setTraceType(canonicalType);
        graphTrace.setTraceOrder(index);
        graphTrace.setDataMode(resolveDataMode(canonicalType, trace));
        Map<String, Object> traceConfig = extractTraceConfig(trace);
        if (timeSeriesTrace) {
            traceConfig.remove(INTERNAL_CUSTOMDATA_FIELD);
        }
        if ("bar".equals(canonicalType)) {
            String explicitBarOrientation = resolveExplicitBarOrientation(trace);
            traceConfig.put("orientation", explicitBarOrientation == null ? "h" : explicitBarOrientation);
        }
        graphTrace.setTraceConfig(traceConfig);

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
                for (GraphCategoryPoint point : filteredCategoryPoints(trace)) {
                    labels.add(resolveLabelValue(point));
                    values.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                }
                result.put("labels", List.copyOf(labels));
                result.put("values", List.copyOf(values));
            }
            case "indicator" -> {
                List<GraphCategoryPoint> points = filteredCategoryPoints(trace);
                GraphCategoryPoint point = points.isEmpty() ? null : points.getFirst();
                Number value = point == null
                    ? 0L
                    : resolveNumericValue(point.getValueNumeric(), point.getValueText());
                result.put("value", value);
            }
            case "bar" -> {
                List<Object> xValues = new ArrayList<>();
                List<Object> yValues = new ArrayList<>();
                List<String> barColors = new ArrayList<>();
                String orientation = resolveStoredBarOrientation(trace);

                for (GraphCategoryPoint point : filteredCategoryPoints(trace)) {
                    if ("v".equals(orientation)) {
                        xValues.add(resolveLabelValue(point));
                        yValues.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                    } else {
                        xValues.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                        yValues.add(resolveLabelValue(point));
                    }
                    barColors.add(resolveBarPointColor(point, trace));
                }

                result.putAll(Map.of(
                    "orientation", orientation,
                    "x", List.copyOf(xValues),
                    "y", List.copyOf(yValues)
                ));
                List<String> normalizedBarColors = normalizeBarColors(barColors);
                if (!normalizedBarColors.isEmpty()) {
                    Map<String, Object> marker = extractTraceMarker(result);
                    marker.put("color", normalizedBarColors.getFirst());
                    marker.put("colors", List.copyOf(normalizedBarColors));
                    result.put("marker", marker);
                }
            }
            case "scatter" -> {
                List<Object> xValues = new ArrayList<>();
                List<Object> yValues = new ArrayList<>();
                List<Object> customDataValues = new ArrayList<>();
                boolean hasPointCustomData = false;

                if ("time_series".equals(trace.getDataMode())) {
                    for (GraphTimeSeriesPoint point : filteredTimeSeriesPoints(trace)) {
                        Map<String, Object> pointMeta = point.getPointMeta();
                        xValues.add(resolveXValue(pointMeta, point.getObservedAt()));
                        yValues.add(resolveNumericValue(point.getYNumeric(), point.getYText()));
                        Object customData = pointMeta == null ? null : pointMeta.get(INTERNAL_CUSTOMDATA_FIELD);
                        customDataValues.add(normalizeJsonValue(customData));
                        if (customData != null) {
                            hasPointCustomData = true;
                        }
                    }
                } else {
                    for (GraphCategoryPoint point : filteredCategoryPoints(trace)) {
                        xValues.add(resolveCategoricalScatterXValue(point));
                        yValues.add(resolveNumericValue(point.getValueNumeric(), point.getValueText()));
                    }
                }

                result.put("x", List.copyOf(xValues));
                result.put("y", List.copyOf(yValues));
                if (hasPointCustomData) {
                    result.put(INTERNAL_CUSTOMDATA_FIELD, List.copyOf(customDataValues));
                }
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
        List<?> xValues = optionalList(trace, "x");
        if (xValues == null) {
            xValues = List.of();
        }
        List<?> yValues = optionalList(trace, "y");
        if (yValues == null) {
            yValues = List.of();
        }
        boolean hasExplicitX = !xValues.isEmpty();
        String canonicalType = resolveCanonicalTraceType(trace);
        boolean barTrace = "bar".equals(canonicalType);
        boolean scatterTrace = "scatter".equals(canonicalType);
        String explicitBarOrientation = barTrace ? resolveExplicitBarOrientation(trace) : null;
        boolean horizontalBarTrace = barTrace && isHorizontalBarInput(xValues, yValues, explicitBarOrientation);
        List<?> barColors = barTrace ? optionalMarkerColors(trace) : null;
        String fallbackBarColor = barTrace ? optionalMarkerColor(trace) : null;

        if (!horizontalBarTrace && hasExplicitX && yValues.isEmpty()) {
            throw new IllegalArgumentException("Graph data is invalid");
        }

        if (hasExplicitX && looksLikeTimeSeries(trace)) {
            graphTrace.getCategoryPoints().clear();
            populateTimeSeriesPoints(graphTrace, trace);
            return;
        }

        for (int index = 0; index < yValues.size(); index++) {
            GraphCategoryPoint point = categoryPointAt(graphTrace, index);
            Object rawX = hasExplicitX && index < xValues.size() ? xValues.get(index) : index;
            Object rawY = yValues.get(index);
            point.setPointOrder(index);

            if (horizontalBarTrace) {
                Number number;
                if (hasExplicitX) {
                    if (!(rawX instanceof Number explicitNumber)) {
                        throw new IllegalArgumentException("Graph data is invalid");
                    }
                    number = explicitNumber;
                } else if (rawY instanceof Number implicitNumber) {
                    number = implicitNumber;
                } else {
                    throw new IllegalArgumentException("Graph data is invalid");
                }
                Object rawLabel = hasExplicitX ? rawY : rawX;
                point.setCategoryKey(String.valueOf(rawLabel));
                point.setCategoryLabel(rawLabel instanceof String ? (String) rawLabel : String.valueOf(rawLabel));
                point.setValueNumeric(toBigDecimal(number));
                Map<String, Object> pointMeta = new LinkedHashMap<>();
                pointMeta.put(INTERNAL_LABEL_FIELD, point.getCategoryLabel());
                addBarPointColor(pointMeta, barColors, fallbackBarColor, index);
                point.setPointMeta(pointMeta);
                continue;
            }

            if (!(rawY instanceof Number number)) {
                throw new IllegalArgumentException("Graph data is invalid");
            }

            point.setCategoryKey(String.valueOf(rawX));
            point.setCategoryLabel(rawX instanceof String ? (String) rawX : String.valueOf(rawX));
            point.setValueNumeric(toBigDecimal(number));
            Map<String, Object> pointMeta = new LinkedHashMap<>();
            pointMeta.put(INTERNAL_LABEL_FIELD, point.getCategoryLabel());
            if (scatterTrace) {
                pointMeta.put(INTERNAL_X_FIELD, rawX);
            }
            if (barTrace) {
                addBarPointColor(pointMeta, barColors, fallbackBarColor, index);
            }
            point.setPointMeta(pointMeta);
        }
        trimPoints(graphTrace.getCategoryPoints(), yValues.size());
    }

    private static void populateTimeSeriesPoints(GraphTrace graphTrace, Map<String, Object> trace) {
        List<?> yValues = requireList(trace, "y");
        List<?> xValues = optionalList(trace, "x");
        List<?> customDataValues = optionalList(trace, INTERNAL_CUSTOMDATA_FIELD);
        if (xValues == null) {
            xValues = List.of();
        }
        if (customDataValues == null) {
            customDataValues = List.of();
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
            Map<String, Object> pointMeta = new LinkedHashMap<>();
            pointMeta.put(INTERNAL_X_FIELD, rawX);
            if (index < customDataValues.size()) {
                pointMeta.put(INTERNAL_CUSTOMDATA_FIELD, normalizeJsonValue(customDataValues.get(index)));
            }
            point.setPointMeta(pointMeta);
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

    private static String resolveExplicitBarOrientation(Map<String, Object> trace) {
        Object rawOrientation = trace.get("orientation");
        if (rawOrientation == null) {
            return null;
        }
        if (!(rawOrientation instanceof String orientation)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }
        String normalized = orientation.strip().toLowerCase(Locale.ROOT);
        if ("h".equals(normalized) || "v".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Graph data is invalid");
    }

    private static boolean isHorizontalBarInput(List<?> xValues, List<?> yValues, String explicitBarOrientation) {
        if ("h".equals(explicitBarOrientation)) {
            return true;
        }
        if ("v".equals(explicitBarOrientation)) {
            return false;
        }
        return !xValues.isEmpty() && !yValues.isEmpty() && isNumericList(xValues) && isScalarList(yValues);
    }

    private static String resolveStoredBarOrientation(GraphTrace trace) {
        Map<String, Object> traceConfig = trace.getTraceConfig();
        if (traceConfig == null) {
            return "h";
        }
        Object rawOrientation = traceConfig.get("orientation");
        if (rawOrientation instanceof String orientation) {
            String normalized = orientation.strip().toLowerCase(Locale.ROOT);
            if ("h".equals(normalized) || "v".equals(normalized)) {
                return normalized;
            }
        }
        return "h";
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

    private static List<?> optionalMarkerColors(Map<String, Object> trace) {
        if (trace == null) {
            return null;
        }
        Object markerValue = trace.get("marker");
        if (!(markerValue instanceof Map<?, ?> marker)) {
            return null;
        }
        Object colorsValue = marker.get("colors");
        if (colorsValue == null) {
            return null;
        }
        if (!(colorsValue instanceof List<?> colors)) {
            throw new IllegalArgumentException("Graph data is invalid");
        }
        return colors;
    }

    private static String optionalMarkerColor(Map<String, Object> trace) {
        if (trace == null) {
            return null;
        }
        Object markerValue = trace.get("marker");
        if (!(markerValue instanceof Map<?, ?> marker)) {
            return null;
        }
        Object colorValue = marker.get("color");
        return colorValue instanceof String color && !color.isBlank() ? color : null;
    }

    private static void addBarPointColor(
        Map<String, Object> pointMeta,
        List<?> barColors,
        String fallbackBarColor,
        int index
    ) {
        if (pointMeta == null) {
            return;
        }
        if (barColors != null && index >= 0 && index < barColors.size()) {
            Object rawColor = barColors.get(index);
            if (rawColor instanceof String color && !color.isBlank()) {
                pointMeta.put(INTERNAL_COLOR_FIELD, color);
                return;
            }
        }
        if (fallbackBarColor != null && !fallbackBarColor.isBlank()) {
            pointMeta.put(INTERNAL_COLOR_FIELD, fallbackBarColor);
        }
    }

    private static Map<String, Object> extractTraceMarker(Map<String, Object> trace) {
        Object markerValue = trace.get("marker");
        if (!(markerValue instanceof Map<?, ?> markerMap)) {
            return new LinkedHashMap<>();
        }
        return copyStringKeyedMap(markerMap);
    }

    private static Map<String, Object> copyStringKeyedMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }

    private static List<String> normalizeBarColors(List<String> barColors) {
        if (barColors == null || barColors.isEmpty()) {
            return List.of();
        }

        String fallbackColor = null;
        for (String color : barColors) {
            if (hasText(color)) {
                fallbackColor = color;
                break;
            }
        }
        if (!hasText(fallbackColor)) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>(barColors.size());
        for (String color : barColors) {
            normalized.add(hasText(color) ? color : fallbackColor);
        }
        return List.copyOf(normalized);
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
        point.setPointMeta(point.getPointMeta());
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
        point.setPointMeta(point.getPointMeta());
        return point;
    }

    private static List<GraphCategoryPoint> filteredCategoryPoints(GraphTrace trace) {
        if (trace.getCategoryPoints() == null || trace.getCategoryPoints().isEmpty()) {
            return List.of();
        }
        return trace.getCategoryPoints().stream()
            .filter(Objects::nonNull)
            .sorted(java.util.Comparator.comparingInt(GraphCategoryPoint::getPointOrder))
            .toList();
    }

    private static List<GraphTimeSeriesPoint> filteredTimeSeriesPoints(GraphTrace trace) {
        if (trace.getTimeSeriesPoints() == null || trace.getTimeSeriesPoints().isEmpty()) {
            return List.of();
        }
        return trace.getTimeSeriesPoints().stream()
            .filter(Objects::nonNull)
            .sorted(java.util.Comparator.comparingInt(GraphTimeSeriesPoint::getPointOrder))
            .toList();
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

    private static boolean isNumericList(List<?> values) {
        for (Object value : values) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isScalarList(List<?> values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                continue;
            }
            return false;
        }
        return true;
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

    private static Object resolveCategoricalScatterXValue(GraphCategoryPoint point) {
        if (point == null) {
            return null;
        }
        Map<String, Object> pointMeta = point.getPointMeta();
        if (pointMeta != null && pointMeta.containsKey(INTERNAL_X_FIELD)) {
            return pointMeta.get(INTERNAL_X_FIELD);
        }
        if (point.getCategoryLabel() != null) {
            return point.getCategoryLabel();
        }
        if (point.getCategoryKey() != null) {
            return point.getCategoryKey();
        }
        return point.getPointOrder();
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

    private static String resolveBarPointColor(GraphCategoryPoint point, GraphTrace trace) {
        if (point != null && point.getPointMeta() != null) {
            Object pointColor = point.getPointMeta().get(INTERNAL_COLOR_FIELD);
            if (pointColor instanceof String color && hasText(color)) {
                return color;
            }
        }

        Map<String, Object> traceConfig = trace == null ? null : trace.getTraceConfig();
        if (traceConfig != null) {
            Object markerValue = traceConfig.get("marker");
            if (markerValue instanceof Map<?, ?> marker) {
                Object markerColor = marker.get("color");
                if (markerColor instanceof String color && hasText(color)) {
                    return color;
                }
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
            try {
                return LocalDate.parse(value, FLEXIBLE_LOCAL_DATE_FORMATTER)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
