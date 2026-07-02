package com.aphinity.client_analytics_core.api.core.services.location;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationGraphTemplateFactoryTest {
    private final LocationGraphTemplateFactory factory = new LocationGraphTemplateFactory();

    @Test
    void createIndicatorTemplateUsesTheSharedGaugeContract() {
        LocationGraphTemplateFactory.GraphTemplate template = factory.create("  InDiCaToR  ", "Phoenix");

        assertEquals("New Indicator Graph", template.name());
        assertEquals(
            List.of(Map.of(
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
                    "bgcolor", "#6b728040",
                    "bar", Map.of("color", "#1f77b4"),
                    "borderwidth", 0,
                    "steps", List.of(
                        Map.of("color", "#6b728040", "range", List.of(0, 100))
                    ),
                    "threshold", Map.of(
                            "line", Map.of("color", "red", "width", 2),
                            "thickness", 0.75,
                            "value", 0
                    )
                )
            )),
            template.data()
        );
        assertEquals(
            Map.of(
                "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
                "showlegend", false,
                "meta", Map.of("aphinitySize", "half")
            ),
            template.layout()
        );
        assertEquals(Map.of("displayModeBar", false, "responsive", false), template.config());
        assertEquals(
            Map.of(
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
            ),
            template.style()
        );
    }

    @Test
    void createBarTemplateUsesHorizontalOrientationByDefault() {
        LocationGraphTemplateFactory.GraphTemplate template = factory.create("bar", "Phoenix");

        assertEquals("New Bar Graph", template.name());
        assertEquals(
            List.of(Map.of(
                "type", "bar",
                "name", "Trace 1",
                "x", List.of(),
                "y", List.of(),
                "orientation", "h",
                "marker", Map.of("color", "#1f77b4")
            )),
            template.data()
        );
        assertEquals(
            Map.of(
                "title", Map.of("x", 0.02, "text", "Phoenix", "xanchor", "left"),
                "xaxis", Map.of("title", "Value"),
                "yaxis", Map.of("automargin", true),
                "margin", Map.of("t", 45, "r", 20, "b", 40, "l", 150),
                "showlegend", false,
                "meta", Map.of("aphinitySize", "full")
            ),
            template.layout()
        );
        assertEquals(Map.of("displayModeBar", false, "responsive", false), template.config());
        assertEquals(
            Map.of(
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
            ),
            template.style()
        );
    }

    @Test
    void createScatterTemplateUsesHvhLinesByDefault() {
        LocationGraphTemplateFactory.GraphTemplate template = factory.create("scatter", "Phoenix");

        assertEquals("New Plot Graph", template.name());
        assertEquals(
            List.of(Map.of(
                "type", "scatter",
                "name", "Trace 1",
                "x", List.of(),
                "y", List.of(),
                "line", Map.of(
                    "color", "#1f77b4",
                    "width", 2,
                    "shape", "hv",
                    "smoothing", 1.0d
                ),
                "mode", "lines+markers",
                "marker", Map.of("size", 6)
            )),
            template.data()
        );
        assertEquals(
            Map.of(
                "title", Map.of("x", 0.02, "text", "Phoenix", "xanchor", "left"),
                "xaxis", Map.of("type", "date", "tickformat", "%b %Y", "tickangle", 0),
                "yaxis", Map.of("range", List.of(0, 100), "title", "Value", "dtick", 1),
                "legend", Map.of("x", 0, "y", -0.3, "orientation", "h"),
                "margin", Map.of("b", 60, "l", 50, "r", 20, "t", 50),
                "meta", Map.of("aphinitySize", "full")
            ),
            template.layout()
        );
    }

    @Test
    void createRejectsUnsupportedGraphTypes() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("donut", "Phoenix"));
        assertThrows(IllegalArgumentException.class, () -> factory.create(null, "Phoenix"));
    }
}
