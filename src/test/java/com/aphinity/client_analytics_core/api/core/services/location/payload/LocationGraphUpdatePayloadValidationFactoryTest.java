package com.aphinity.client_analytics_core.api.core.services.location.payload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationGraphUpdatePayloadValidationFactoryTest {
    private final LocationGraphUpdatePayloadValidationFactory factory = new LocationGraphUpdatePayloadValidationFactory();

    @Test
    void validateForUpdateAcceptsIndicatorPayloadsThatPreserveTheIndicatorContract() {
        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(indicatorTrace(68)),
            List.of(indicatorTrace(72)),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(indicatorTrace(72)), payload.data());
        assertEquals(Map.of("showlegend", false), payload.layout());
    }

    @Test
    void validateForUpdateAcceptsScatterglPayloadsAsCartesianUpdates() {
        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(scatterGlTrace(3, 5)),
            List.of(scatterGlTrace(4, 6)),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(scatterGlTrace(4, 6)), payload.data());
        assertEquals(Map.of("showlegend", false), payload.layout());
    }

    @Test
    void validateForUpdateAcceptsLegacyCartesianPayloadsWithoutExplicitXValues() {
        Map<String, Object> storedTrace = Map.of("type", "bar", "y", List.of(1, 2, 3));
        Map<String, Object> nextTrace = Map.of("type", "bar", "y", List.of(4, 5, 6));

        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(storedTrace),
            List.of(nextTrace),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(nextTrace), payload.data());
        assertEquals(Map.of("showlegend", false), payload.layout());
    }

    @Test
    void validateForUpdateRejectsGraphTypeChanges() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(indicatorTrace(68)),
                List.of(pieTrace()),
                Map.of("showlegend", false)
            )
        );
    }

    @Test
    void validateForUpdateRejectsInvalidPieValues() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(pieTrace(3, 7)),
                List.of(pieTrace(3, "bad")),
                Map.of("showlegend", false)
            )
        );
    }

    @Test
    void validateForUpdateRejectsOutOfRangeIndicatorValues() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(indicatorTrace(68)),
                List.of(indicatorTrace(101)),
                Map.of("showlegend", false)
            )
        );
    }

    @Test
    void validateForUpdateRejectsNullNextDataPayload() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(barTrace(1, 2, 3)),
                null,
                Map.of("showlegend", false)
            )
        );
    }

    @Test
    void validateForUpdateRejectsMalformedStoredIndicatorPayloads() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(indicatorTrace(101)),
                List.of(indicatorTrace(72)),
                Map.of("showlegend", false)
            )
        );
    }

    @Test
    void validateForUpdateRejectsNonObjectLayouts() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(barTrace(1, 2, 3)),
                List.of(barTrace(4, 5, 6)),
                List.of("invalid")
            )
        );
    }

    private Map<String, Object> indicatorTrace(int value) {
        return Map.of(
            "type", "indicator",
            "mode", "gauge+number",
            "value", value,
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
        );
    }

    private Map<String, Object> pieTrace(int... values) {
        return Map.of(
            "type", "pie",
            "labels", List.of("Open", "Closed"),
            "values", values.length == 0 ? List.of() : toIntegerList(values),
            "marker", Map.of("color", "#1f77b4")
        );
    }

    private Map<String, Object> pieTrace(int firstValue, String invalidSecondValue) {
        return Map.of(
            "type", "pie",
            "labels", List.of("Open", "Closed"),
            "values", List.of(firstValue, invalidSecondValue),
            "marker", Map.of("color", "#1f77b4")
        );
    }

    private Map<String, Object> barTrace(int... values) {
        return Map.of(
            "type", "bar",
            "x", List.of("Jan", "Feb", "Mar"),
            "y", values.length == 0 ? List.of() : toIntegerList(values),
            "marker", Map.of("color", "#1f77b4")
        );
    }

    private List<Integer> toIntegerList(int... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private Map<String, Object> scatterGlTrace(int... values) {
        return Map.of(
            "type", "scattergl",
            "x", List.of("Jan", "Feb"),
            "y", values.length == 0 ? List.of() : toIntegerList(values),
            "marker", Map.of("color", "#1f77b4")
        );
    }
}
