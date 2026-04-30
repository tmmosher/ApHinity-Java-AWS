package com.aphinity.client_analytics_core.api.core.services.location;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LocationGraphTemplateFactory {
    private static final String DEFAULT_GRAPH_COLOR = "#1f77b4";

    public record GraphTemplate(
        String name,
        List<Map<String, Object>> data,
        Map<String, Object> layout,
        Map<String, Object> config,
        Map<String, Object> style
    ) {
    }

    private enum GraphTemplateType {
        PIE,
        INDICATOR,
        BAR,
        SCATTER
    }

    public GraphTemplate create(String rawGraphType, String locationName) {
        return switch (parseGraphTemplateType(rawGraphType)) {
            case PIE -> new GraphTemplate(
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
                Map.of(
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
                ),
                buildDefaultGraphConfig(),
                buildCompactGraphStyle()
            );
            case INDICATOR -> new GraphTemplate(
                "New Indicator Graph",
                buildIndicatorTemplateData(),
                buildIndicatorTemplateLayout(),
                buildDefaultGraphConfig(),
                buildCompactGraphStyle()
            );
            case BAR -> new GraphTemplate(
                "New Bar Graph",
                List.of(Map.of(
                    "type", "bar",
                    "name", "Trace 1",
                    "x", List.of(),
                    "y", List.of(),
                    "orientation", "h",
                    "marker", Map.of("color", DEFAULT_GRAPH_COLOR)
                )),
                Map.of(
                    "title", buildGraphTitle(locationName),
                    "margin", Map.of("t", 24, "r", 24, "b", 48, "l", 48),
                    "showlegend", false
                ),
                buildDefaultGraphConfig(),
                Map.of("height", 320)
            );
            case SCATTER -> new GraphTemplate(
                "New Plot Graph",
                buildScatterTemplateData(),
                buildScatterTemplateLayout(locationName),
                buildScatterTemplateConfig(),
                buildScatterTemplateStyle()
            );
        };
    }

    private GraphTemplateType parseGraphTemplateType(String rawGraphType) {
        if (rawGraphType == null) {
            throw new IllegalArgumentException("Graph type is invalid");
        }

        return switch (rawGraphType.strip().toLowerCase(Locale.ROOT)) {
            case "pie" -> GraphTemplateType.PIE;
            case "indicator" -> GraphTemplateType.INDICATOR;
            case "bar" -> GraphTemplateType.BAR;
            case "scatter", "line" -> GraphTemplateType.SCATTER;
            default -> throw new IllegalArgumentException("Graph type is invalid");
        };
    }

    private Map<String, Object> buildDefaultGraphConfig() {
        return Map.of(
            "displayModeBar", false,
            "responsive", false
        );
    }

    private List<Map<String, Object>> buildIndicatorTemplateData() {
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
                    "bar", Map.of("color", DEFAULT_GRAPH_COLOR),
                    "borderwidth", 0,
                    "steps", List.of(
                        Map.of("color", "#80000030", "range", List.of(0, 30)),
                        Map.of("color", "#FF000030", "range", List.of(30, 60)),
                        Map.of("color", "#FFFF0030", "range", List.of(60, 90)),
                        Map.of("color", "#00800030", "range", List.of(90, 100))
                        ),
                    "threshold", Map.of(
                        "line", Map.of("color", "red", "width", 2),
                        "thickness", 0.75,
                        "value", 90
                        )
                    )
                )
        );
    }

    private Map<String, Object> buildIndicatorTemplateLayout() {
        return Map.of(
            "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
            "showlegend", false
        );
    }

    private Map<String, Object> buildGraphTitle(String locationName) {
        return Map.of(
            "x", 0.02,
            "text", locationName == null ? "" : locationName,
            "xanchor", "left"
        );
    }

    private Map<String, Object> buildCompactGraphStyle() {
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

    private List<Map<String, Object>> buildScatterTemplateData() {
        return List.of(
            Map.of(
                "type", "scatter",
                "name", "Trace 1",
                "x", List.of(),
                "y", List.of(),
                "line", Map.of(
                    "color", DEFAULT_GRAPH_COLOR,
                    "width", 2
                ),
                "mode", "lines+markers",
                "marker", Map.of("size", 6)
            )
        );
    }

    private Map<String, Object> buildScatterTemplateLayout(String locationName) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("title", buildGraphTitle(locationName));
        layout.put("xaxis", Map.of(
            "type", "date",
            "tickformat", "%b %Y"
        ));
        layout.put("yaxis", Map.of(
            "range", List.of(0, 100),
            "title", "% Compliance",
            "ticksuffix", "%"
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
        return layout;
    }

    private Map<String, Object> buildScatterTemplateConfig() {
        return Map.of(
            "displayModeBar", false,
            "responsive", false
        );
    }

    private Map<String, Object> buildScatterTemplateStyle() {
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
}
