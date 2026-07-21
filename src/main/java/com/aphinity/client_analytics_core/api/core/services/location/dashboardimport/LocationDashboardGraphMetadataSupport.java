package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphAnchor;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphDimension;

/**
 * Shared graph payload metadata helpers for dashboard import.
 * These routines centralize layout/meta conventions so matching, merging, and
 * derived graph reconstruction all interpret persisted graph payloads the same way.
 */
final class LocationDashboardGraphMetadataSupport {
    static final String IMPORT_LAYOUT_META_KEY = "aphinityImport";
    static final String GRAPH_SIZE_LAYOUT_META_KEY = "aphinitySize";
    private static final String GRAPH_SIZE_HALF = "half";
    private static final String GRAPH_SIZE_FULL = "full";
    private static final String GRAPH_SIZE_DUPLEX = "duplex";
    private static final String GRAPH_SIZE_DOUBLE = "double";
    private static final Set<String> LEGACY_IMPORTED_Y_AXIS_TITLES = Set.of(
        "% Compliance",
        "% Conformance"
    );

    private LocationDashboardGraphMetadataSupport() {
    }

    static Map<String, String> readImportMetadata(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return Map.of();
        }
        Object metaValue = graph.getLayout().get("meta");
        if (!(metaValue instanceof Map<?, ?> meta)) {
            return Map.of();
        }
        Object importMetaValue = meta.get(IMPORT_LAYOUT_META_KEY);
        if (!(importMetaValue instanceof Map<?, ?> importMeta)) {
            return Map.of();
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        importMeta.forEach((key, value) -> {
            if (key != null && value != null) {
                metadata.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return Map.copyOf(metadata);
    }

    static Map<String, Object> withImportMetadataAndDefaults(
        Map<String, Object> existingLayout,
        GraphConfig graphDefinition,
        String strategyLocationName
    ) {
        Map<String, Object> layout = copyMutableMap(existingLayout);
        if (readLayoutTitleText(layout.get("title")) == null) {
            layout.put("title", buildGraphTitle(graphDefinition.title()));
        }

        Map<String, Object> importMeta = new LinkedHashMap<>();
        importMeta.put("graphId", graphDefinition.id());
        importMeta.put("graphName", graphDefinition.name());
        importMeta.put("graphTitle", graphDefinition.title());
        importMeta.put("importType", graphDefinition.importType().value());
        importMeta.put("metricKey", "non_conformance_count");
        GraphAnchor anchor = graphDefinition.effectiveAnchor();
        importMeta.put("anchorDimension", anchor.dimension().value());
        importMeta.put("anchorKey", anchor.key());
        importMeta.put("traceBy", graphDefinition.effectiveTraceBy().value());
        if (anchor.dimension() == GraphDimension.SUBLOCATION) {
            importMeta.put("sublocationKey", anchor.key());
        }
        importMeta.put("unit", "count");
        importMeta.put("locationName", strategyLocationName);

        Map<String, Object> meta = copyMutableMap(asMap(layout.get("meta")));
        meta.put(IMPORT_LAYOUT_META_KEY, importMeta);
        meta.put(GRAPH_SIZE_LAYOUT_META_KEY, graphSizeForType(graphDefinition.graphType()));
        layout.put("meta", meta);

        Map<String, Object> xAxis = copyMutableMap(asMap(layout.get("xaxis")));
        xAxis.put("type", "date");
        xAxis.putIfAbsent("tickformat", "%b %Y");
        xAxis.put("tickangle", 0);
        layout.put("xaxis", xAxis);

        Map<String, Object> yAxis = copyMutableMap(asMap(layout.get("yaxis")));
        yAxis.remove("range");
        yAxis.put("rangemode", "tozero");
        putImportDefaultAxisTitle(yAxis, "# Non-Conformances");
        yAxis.put("dtick", 1);
        yAxis.remove("ticksuffix");
        layout.put("yaxis", yAxis);

        return layout;
    }

    static boolean hasLegacyPercentAxis(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return false;
        }
        Map<String, Object> yAxis = asMap(graph.getLayout().get("yaxis"));
        String title = readLayoutTitleText(yAxis.get("title"));
        Object tickSuffix = yAxis.get("ticksuffix");
        return (title != null && title.contains("%"))
            || (tickSuffix != null && String.valueOf(tickSuffix).contains("%"));
    }

    static Map<String, Object> withDerivedImportMetadata(
        Map<String, Object> existingLayout,
        DerivedGraphConfig derivedGraphDefinition,
        String strategyLocationName
    ) {
        return withDerivedImportMetadata(existingLayout, derivedGraphDefinition, strategyLocationName, List.of());
    }

    static Map<String, Object> withDerivedImportMetadata(
        Map<String, Object> existingLayout,
        DerivedGraphConfig derivedGraphDefinition,
        String strategyLocationName,
        List<Map<String, Object>> traces
    ) {
        Map<String, Object> layout = copyMutableMap(existingLayout);
        if (derivedGraphDefinition.title() != null
            && readLayoutTitleText(layout.get("title")) == null) {
            layout.put("title", buildGraphTitle(derivedGraphDefinition.title()));
        }

        Map<String, Object> meta = copyMutableMap(asMap(layout.get("meta")));
        Map<String, Object> importMeta = copyMutableMap(asMap(meta.get(IMPORT_LAYOUT_META_KEY)));
        importMeta.put("derivedGraphId", derivedGraphDefinition.id());
        importMeta.put("graphName", derivedGraphDefinition.name());
        if (derivedGraphDefinition.title() != null) {
            importMeta.put("graphTitle", derivedGraphDefinition.title());
        }
        importMeta.put(
            "derivedGraphType",
            LocationDashboardDerivedGraphSupport.metadataValue(derivedGraphDefinition.derivedType())
        );
        importMeta.put("metricKey", derivedMetricKey(derivedGraphDefinition.derivedType()));
        importMeta.put("unit", derivedGraphUnit(derivedGraphDefinition.derivedType()));
        if (derivedGraphDefinition.derivedType() == LocationDashboardImportStrategyConfig.DerivedGraphType.RECENT_SAMPLE_MEASUREMENTS) {
            importMeta.put("renderer", "tabulator");
        }
        importMeta.put("locationName", strategyLocationName);
        meta.put(IMPORT_LAYOUT_META_KEY, importMeta);
        meta.put(GRAPH_SIZE_LAYOUT_META_KEY, graphSizeForType(derivedGraphDefinition.graphType()));
        layout.put("meta", meta);
        if ("bar".equals(normalizeGraphType(derivedGraphDefinition.graphType()))) {
            applyDerivedBarLayoutDefaults(layout, derivedGraphDefinition, traces);
        }
        return layout;
    }

    static Map<String, Object> withDerivedImportStyle(
        Map<String, Object> existingStyle,
        DerivedGraphConfig derivedGraphDefinition
    ) {
        Map<String, Object> style = copyMutableMap(existingStyle);
        if ("bar".equals(normalizeGraphType(derivedGraphDefinition.graphType()))) {
            style.put("theme", Map.of(
                "dark", Map.of(
                    "gridColor", "rgba(148, 163, 184, 0.3)",
                    "textColor", "#e5e7eb"
                ),
                "light", Map.of(
                    "gridColor", "rgba(15, 23, 42, 0.15)",
                    "textColor", "#111827"
                )
            ));
            style.put("height", 320);
        } else if ("table".equals(normalizeGraphType(derivedGraphDefinition.graphType()))
            || "sunburst".equals(normalizeGraphType(derivedGraphDefinition.graphType()))) {
            style.put("height", 640);
        }
        return style;
    }

    private static String graphSizeForType(String rawGraphType) {
        return switch (normalizeGraphType(rawGraphType)) {
            case "pie", "indicator" -> GRAPH_SIZE_HALF;
            case "table" -> GRAPH_SIZE_DOUBLE;
            case "sunburst" -> GRAPH_SIZE_DUPLEX;
            default -> GRAPH_SIZE_FULL;
        };
    }

    private static void applyDerivedBarLayoutDefaults(
        Map<String, Object> layout,
        DerivedGraphConfig derivedGraphDefinition,
        List<Map<String, Object>> traces
    ) {
        boolean horizontal = resolveDerivedBarHorizontal(derivedGraphDefinition, traces);
        Map<String, Object> xAxis = copyMutableMap(asMap(layout.get("xaxis")));
        Map<String, Object> yAxis = copyMutableMap(asMap(layout.get("yaxis")));
        if (horizontal) {
            putDefaultAxisTitle(xAxis, derivedGraphDefinition.name());
            yAxis.put("automargin", true);
            yAxis.remove("title");
        } else {
            xAxis.put("automargin", true);
            xAxis.remove("title");
            putDefaultAxisTitle(yAxis, derivedGraphDefinition.name());
        }
        layout.put("xaxis", xAxis);
        layout.put("yaxis", yAxis);
        layout.put("margin", horizontal
            ? Map.of("b", 40, "l", 150, "r", 20, "t", 45)
            : Map.of("b", 80, "l", 60, "r", 20, "t", 45));
        if (horizontal) {
            layout.put("showlegend", false);
        }
    }

    /**
     * Axis titles edited by a user are part of the persisted graph layout.
     * Import and derived graph refreshes call this class while rebuilding other
     * metadata, so generated titles must only fill an unset axis title. The
     * import path separately recognizes the two pre-count generated labels so
     * legacy graphs can still be migrated.
     */
    private static void putDefaultAxisTitle(Map<String, Object> axis, String defaultTitle) {
        if (readLayoutTitleText(axis.get("title")) == null) {
            axis.put("title", defaultTitle);
        }
    }

    private static void putImportDefaultAxisTitle(Map<String, Object> axis, String defaultTitle) {
        String existingTitle = readLayoutTitleText(axis.get("title"));
        if (existingTitle == null || LEGACY_IMPORTED_Y_AXIS_TITLES.contains(existingTitle)) {
            axis.put("title", defaultTitle);
        }
    }

    private static boolean resolveDerivedBarHorizontal(
        DerivedGraphConfig derivedGraphDefinition,
        List<Map<String, Object>> traces
    ) {
        String configuredOrientation = traces == null ? null : traces.stream()
            .filter(trace -> trace != null && "bar".equals(normalizeGraphType(String.valueOf(trace.get("type")))))
            .map(trace -> trace.get("orientation"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(value -> value.strip().toLowerCase(Locale.ROOT))
            .filter(value -> "h".equals(value) || "v".equals(value))
            .findFirst()
            .orElse(null);
        if (configuredOrientation != null) {
            return "h".equals(configuredOrientation);
        }
        return derivedGraphDefinition.derivedType()
            != LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_STATUS_BY_FACILITY;
    }

    private static String derivedMetricKey(LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType) {
        return derivedGraphType == null ? null : derivedGraphType.value();
    }

    private static String derivedGraphUnit(LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType) {
        if (derivedGraphType == null) {
            return null;
        }
        return switch (derivedGraphType) {
            case ACTIVE_NON_CONFORMANCE_PERCENT, PERCENT_CONFORMANCE, PERCENT_RESOLVED -> "percent";
            case RECENT_SAMPLE_MEASUREMENTS -> "samples";
            default -> "count";
        };
    }

    static String readGraphLayoutTitleText(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return null;
        }
        return readLayoutTitleText(graph.getLayout().get("title"));
    }

    static String readLayoutTitleText(Object titleValue) {
        if (titleValue instanceof Map<?, ?> titleMap) {
            Object textValue = titleMap.get("text");
            if (textValue == null) {
                return null;
            }
            String titleText = String.valueOf(textValue);
            return titleText.isBlank() ? null : titleText;
        }
        if (titleValue instanceof String titleText) {
            return titleText.isBlank() ? null : titleText;
        }
        return null;
    }

    static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return Map.of();
        }
        Map<String, Object> copiedMap = new LinkedHashMap<>();
        mapValue.forEach((key, nestedValue) -> {
            if (key != null) {
                copiedMap.put(String.valueOf(key), nestedValue);
            }
        });
        return copiedMap;
    }

    static List<?> asList(Object value) {
        return value instanceof List<?> listValue ? listValue : List.of();
    }

    static Map<String, Object> copyMutableMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    static String normalizeGraphType(String rawGraphType) {
        if (rawGraphType == null || rawGraphType.isBlank()) {
            return "scatter";
        }
        String normalized = rawGraphType.strip().toLowerCase(Locale.ROOT);
        return "line".equals(normalized) ? "scatter" : normalized;
    }

    static List<Map<String, Object>> currentTraceList(Graph graph) {
        if (graph == null) {
            return List.of();
        }
        try {
            return GraphRelationalPayloadMapper.normalize(graph).data();
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    static LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        String rawValue = String.valueOf(value).strip();
        if (rawValue.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String rawString) {
            String normalized = rawString.strip();
            if (normalized.isBlank()) {
                return 0L;
            }
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String nullSafeNormalized(String value) {
        String normalized = normalizeKey(value);
        return normalized == null ? "" : normalized;
    }

    static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{Alnum}]+", " ")
            .replaceAll("\\s+", " ")
            .strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static Map<String, Object> buildGraphTitle(String titleText) {
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("x", 0.02);
        title.put("text", titleText);
        title.put("xanchor", "left");
        return title;
    }
}
