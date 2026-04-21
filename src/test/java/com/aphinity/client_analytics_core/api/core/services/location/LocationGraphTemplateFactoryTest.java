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
                    "bar", Map.of("color", "#1f77b4"),
                    "borderwidth", 0,
                    "steps", List.of(
                        Map.of("color", "#80000030", "range", List.of(0, 30)),
                        Map.of("color", "#FF000030", "range", List.of(30, 60)),
                        Map.of("color", "#FFFF0030", "range", List.of(60, 90)),
                        Map.of("color", "#00800030", "range", List.of(90, 100))
                    )
                )
            )),
            template.data()
        );
        assertEquals(
            Map.of(
                "margin", Map.of("t", 10, "r", 10, "b", 10, "l", 10),
                "showlegend", false
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
    void createRejectsUnsupportedGraphTypes() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("donut", "Phoenix"));
        assertThrows(IllegalArgumentException.class, () -> factory.create(null, "Phoenix"));
    }
}
