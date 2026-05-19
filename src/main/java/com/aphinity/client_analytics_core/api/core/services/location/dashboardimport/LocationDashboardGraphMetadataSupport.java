package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;

/**
 * Shared graph payload metadata helpers for dashboard import.
 * These routines centralize layout/meta conventions so matching, merging, and
 * derived graph reconstruction all interpret persisted graph payloads the same way.
 */
final class LocationDashboardGraphMetadataSupport {
    static final String IMPORT_LAYOUT_META_KEY = "aphinityImport";

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
        importMeta.put("sublocationKey", graphDefinition.sublocationKey());
        importMeta.put("unit", "count");
        importMeta.put("locationName", strategyLocationName);

        Map<String, Object> meta = copyMutableMap(asMap(layout.get("meta")));
        meta.put(IMPORT_LAYOUT_META_KEY, importMeta);
        layout.put("meta", meta);

        Map<String, Object> xAxis = copyMutableMap(asMap(layout.get("xaxis")));
        xAxis.put("type", "date");
        xAxis.putIfAbsent("tickformat", "%b %Y");
        layout.put("xaxis", xAxis);

        Map<String, Object> yAxis = copyMutableMap(asMap(layout.get("yaxis")));
        yAxis.remove("range");
        yAxis.put("rangemode", "tozero");
        yAxis.put("title", "# Non-Conformances");
        yAxis.remove("ticksuffix");
        layout.put("yaxis", yAxis);

        return layout;
    }

    static Map<String, Object> withDerivedImportMetadata(
        Map<String, Object> existingLayout,
        DerivedGraphConfig derivedGraphDefinition,
        String strategyLocationName
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
        importMeta.put("locationName", strategyLocationName);
        meta.put(IMPORT_LAYOUT_META_KEY, importMeta);
        layout.put("meta", meta);
        return layout;
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
