package com.aphinity.client_analytics_core.api.core.services.location;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the initial Plotly payloads used when users create location graphs.
 */
@Component
public class LocationGraphTemplateFactory {
    public static final String GRAPH_DEFINITION_STYLE_KEY = "aphinityDefinition";
    private static final String DEFAULT_GRAPH_COLOR = "#1f77b4";
    private static final String GRAPH_SIZE_LAYOUT_META_KEY = "aphinitySize";
    private static final String GRAPH_SIZE_HALF = "half";
    private static final String GRAPH_SIZE_FULL = "full";
    private static final String GRAPH_SIZE_DUPLEX = "duplex";
    private static final String GRAPH_SIZE_DOUBLE = "double";
    private static final String DEFAULT_TIME_SERIES_LINE_SHAPE = "hv";
    private static final double DEFAULT_TIME_SERIES_LINE_SMOOTHING = 1.0d;
    private static final String INDICATOR_GAUGE_BACKGROUND_COLOR = "#6b728040";
    private static final String INDICATOR_THRESHOLD_COLOR = "red";

    /**
     * Immutable template for a newly-created graph.
     *
     * @param name default graph name
     * @param data initial Plotly trace list
     * @param layout initial Plotly layout
     * @param config initial Plotly config
     * @param style application-specific graph style metadata
     */
    public record GraphTemplate(
        String name,
        List<Map<String, Object>> data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style
    ) {
    }

    private Map<String, LocationGraphDefinition> definitionsByKey;

    public LocationGraphTemplateFactory(List<LocationGraphDefinition> definitions) {
        configureDefinitions(definitions);
    }

    private void configureDefinitions(Iterable<LocationGraphDefinition> definitions) {
        Map<String, LocationGraphDefinition> indexedDefinitions = new LinkedHashMap<>();
        for (LocationGraphDefinition definition : definitions) {
            register(indexedDefinitions, definition.key(), definition);
            for (String alias : definition.aliases()) {
                register(indexedDefinitions, alias, definition);
            }
        }
        this.definitionsByKey = Map.copyOf(indexedDefinitions);
    }

    /**
     * Creates a graph template for the requested graph type.
     *
     * @param rawGraphType request graph type
     * @param locationName location name used for default chart titles
     * @return graph template ready for persistence
     */
    public GraphTemplate create(String rawGraphType, String locationName) {
        String definitionKey = normalizeKey(rawGraphType);
        LocationGraphDefinition definition = definitionKey == null ? null : definitionsByKey.get(definitionKey);
        if (definition == null) {
            throw new IllegalArgumentException("Graph type is invalid");
        }
        GraphTemplate template = definition.createTemplate(locationName);
        Map<String, Object> style = new LinkedHashMap<>(template.style());
        if (!definition.key().equals(definition.traceType())) {
            style.put(GRAPH_DEFINITION_STYLE_KEY, definition.key());
        }
        return new GraphTemplate(
            template.name(), template.data(), template.layout(), template.config(), Map.copyOf(style)
        );
    }

    static GraphTemplate pieTemplate(String locationName) {
        return new GraphTemplate(
                "New Pie Graph",
                List.of(Map.of(
                    "type", "pie",
                    "name", "Trace 1",
                    "hole", 0.72,
                    "sort", false,
                    "labels", List.of("fill"),
                    "values", List.of(0),
                    "marker", Map.of(
                        "color", DEFAULT_GRAPH_COLOR,
                        "colors", List.of(DEFAULT_GRAPH_COLOR)
                    ),
                    "textinfo", "none",
                    "direction", "clockwise",
                    "hovertemplate", "%{label}: %{value}<extra></extra>"
                )),
                withGraphSize(Map.of(
                    "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
                    "showlegend", false,
                    "annotations", List.of(Map.of(
                        "x", 0.5,
                        "y", 0.5,
                        "text", "<b>0</b>",
                        "xref", "paper",
                        "yref", "paper",
                        "showarrow", false,
                        "font", Map.of("size", 22)
                    ))
                ), GRAPH_SIZE_HALF),
                buildDefaultGraphConfig(),
                buildCompactGraphStyle()
            );
    }

    static GraphTemplate indicatorTemplate(String locationName) {
        return new GraphTemplate(
                "New Indicator Graph",
                buildIndicatorTemplateData(),
                buildIndicatorTemplateLayout(),
                buildDefaultGraphConfig(),
                buildCompactGraphStyle()
            );
    }

    static GraphTemplate barTemplate(String locationName) {
        return new GraphTemplate(
                "New Bar Graph",
                List.of(Map.of(
                    "type", "bar",
                    "name", "Trace 1",
                    "x", List.of(),
                    "y", List.of(),
                    "orientation", "h",
                    "marker", Map.of("color", DEFAULT_GRAPH_COLOR)
                )),
                withGraphSize(Map.of(
                    "title", buildGraphTitle(locationName),
                    "xaxis", Map.of("title", "Value"),
                    "yaxis", Map.of("automargin", true),
                    "margin", Map.of("t", 45, "r", 20, "b", 40, "l", 150),
                    "showlegend", false
                ), GRAPH_SIZE_FULL),
                buildDefaultGraphConfig(),
                buildBarGraphStyle()
            );
    }

    static GraphTemplate scatterTemplate(String locationName) {
        return new GraphTemplate(
                "New Plot Graph",
                buildScatterTemplateData(),
                buildScatterTemplateLayout(locationName),
                buildScatterTemplateConfig(),
                buildScatterTemplateStyle()
            );
    }

    static GraphTemplate tableTemplate(String locationName) {
        return new GraphTemplate(
                "New Table Graph",
                buildTableTemplateData(),
                withGraphSize(Map.of(
                    "margin", Map.of("t", 20, "r", 10, "b", 10, "l", 10)
                ), GRAPH_SIZE_DOUBLE),
                buildDefaultGraphConfig(),
                buildFullGraphStyle(640)
            );
    }

    static GraphTemplate sunburstTemplate(String locationName) {
        return new GraphTemplate(
                "New Sunburst Graph",
                List.of(Map.ofEntries(
                    Map.entry("type", "sunburst"),
                    Map.entry("name", "Trace 1"),
                    Map.entry("ids", List.of("sample")),
                    Map.entry("labels", List.of("Sample")),
                    Map.entry("parents", List.of("")),
                    Map.entry("values", List.of(0)),
                    Map.entry("branchvalues", "total"),
                    Map.entry("insidetextorientation", "radial"),
                    Map.entry("sort", false),
                    Map.entry("hovertemplate", "%{label}: %{value}<extra></extra>"),
                    Map.entry("marker", Map.of("line", Map.of("width", 1, "color", "#f6f6f6")))
                )),
                withGraphSize(Map.of(
                    "margin", Map.of("t", 20, "r", 20, "b", 20, "l", 20),
                    "showlegend", false
                ), GRAPH_SIZE_DUPLEX),
                buildDefaultGraphConfig(),
                buildFullGraphStyle(640)
            );
    }

    private static void register(
        Map<String, LocationGraphDefinition> definitions,
        String rawKey,
        LocationGraphDefinition definition
    ) {
        String key = normalizeKey(rawKey);
        if (key == null || definitions.putIfAbsent(key, definition) != null) {
            throw new IllegalStateException("Duplicate or invalid graph definition key: " + rawKey);
        }
    }

    private static String normalizeKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String normalized = rawKey.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static Map<String, Object> buildDefaultGraphConfig() {
        return Map.of(
            "displayModeBar", false,
            "responsive", false
        );
    }

    private static List<Map<String, Object>> buildIndicatorTemplateData() {
        return List.of(
            Map.of(
                "type", "indicator",
                "name", "Trace 1",
                "mode", "gauge+number",
                "value", 0,
                "number", Map.of(
                    "suffix", "%",
                    "font", Map.of("size", 22)
                    ),
                "gauge", Map.of(
                    "shape", "angular",
                    "axis", Map.of("range", List.of(0, 100)),
                    "bgcolor", INDICATOR_GAUGE_BACKGROUND_COLOR,
                    "bar", Map.of("color", DEFAULT_GRAPH_COLOR),
                    "borderwidth", 0,
                    "steps", List.of(
                        Map.of("color", INDICATOR_GAUGE_BACKGROUND_COLOR, "range", List.of(0, 100))
                        ),
                    "threshold", Map.of(
                        "line", Map.of("color", INDICATOR_THRESHOLD_COLOR, "width", 2),
                        "thickness", 0.75,
                        "value", 0
                        )
                    )
                )
        );
    }

    private static Map<String, Object> buildIndicatorTemplateLayout() {
        return withGraphSize(Map.of(
            "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
            "showlegend", false
        ), GRAPH_SIZE_HALF);
    }

    private static Map<String, Object> buildGraphTitle(String locationName) {
        return Map.of(
            "x", 0.02,
            "text", locationName == null ? "" : locationName,
            "xanchor", "left"
        );
    }

    private static Map<String, Object> buildCompactGraphStyle() {
        return Map.of(
            "theme",
            Map.of(
                "dark", Map.of(
                    "gridColor", "rgba(148, 163, 184, 0.3)",
                    "textColor", "#e5e7eb"
                ),
                "light", Map.of(
                    "gridColor", "rgba(15, 23, 42, 0.15)",
                    "textColor", "#111827"
                )
            ),
            "height", 160
        );
    }

    private static Map<String, Object> buildBarGraphStyle() {
        return buildFullGraphStyle(320);
    }

    private static Map<String, Object> buildFullGraphStyle(int height) {
        Map<String, Object> style = new LinkedHashMap<>(buildCompactGraphStyle());
        style.put("height", height);
        return Map.copyOf(style);
    }

    private static List<Map<String, Object>> buildScatterTemplateData() {
        return List.of(
            Map.of(
                "type", "scatter",
                "name", "Trace 1",
                "x", List.of(),
                "y", List.of(),
                "line", Map.of(
                    "color", DEFAULT_GRAPH_COLOR,
                    "width", 2,
                    "shape", DEFAULT_TIME_SERIES_LINE_SHAPE,
                    "smoothing", DEFAULT_TIME_SERIES_LINE_SMOOTHING
                ),
                "mode", "lines+markers",
                "marker", Map.of("size", 6)
            )
        );
    }

    private static Map<String, Object> buildScatterTemplateLayout(String locationName) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("title", buildGraphTitle(locationName));
        layout.put("xaxis", Map.of(
            "type", "date",
            "tickformat", "%b %Y",
            "tickangle", 0
        ));
        layout.put("yaxis", Map.of(
            "range", List.of(0, 100),
            "title", "Value",
            "dtick", 1
        ));
        layout.put("legend", Map.of(
            "x", 0,
            "y", -0.3,
            "orientation", "h"
        ));
        layout.put("margin", Map.of(
            "b", 60,
            "l", 50,
            "r", 20,
            "t", 50
        ));
        return withGraphSize(layout, GRAPH_SIZE_FULL);
    }

    private static Map<String, Object> buildScatterTemplateConfig() {
        return Map.of(
            "displayModeBar", false,
            "responsive", false
        );
    }

    private static Map<String, Object> buildScatterTemplateStyle() {
        return Map.of(
            "theme",
            Map.of(
                "dark", Map.of(
                    "gridColor", "rgba(148, 163, 184, 0.3)",
                    "textColor", "#e5e7eb"
                ),
                "light", Map.of(
                    "gridColor", "rgba(15, 23, 42, 0.15)",
                    "textColor", "#111827"
                )
            ),
            "height", 320
        );
    }

    private static List<Map<String, Object>> buildTableTemplateData() {
        return List.of(Map.of(
            "type", "table",
            "name", "Trace 1",
            "header", Map.of(
                "values", List.of("Column 1", "Column 2"),
                "align", "left",
                "fill", Map.of("color", "#e5e7eb"),
                "font", Map.of("color", "#111827", "size", 12)
            ),
            "cells", Map.of(
                "values", List.of(List.of(""), List.of("")),
                "align", "left"
            )
        ));
    }

    private static Map<String, Object> withGraphSize(Map<String, Object> rawLayout, String graphSize) {
        Map<String, Object> layout = new LinkedHashMap<>(rawLayout);
        Object rawMeta = layout.get("meta");
        Map<String, Object> meta = rawMeta instanceof Map<?, ?> rawMetaMap
            ? copyUnknownObjectMap(rawMetaMap)
            : new LinkedHashMap<>();
        meta.put(GRAPH_SIZE_LAYOUT_META_KEY, graphSize);
        layout.put("meta", meta);
        return Map.copyOf(layout);
    }

    private static Map<String, Object> copyUnknownObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }
}
