package com.aphinity.client_analytics_core.api.core.services.location.payload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationGraphUpdatePayloadValidationFactoryTest {
    private final LocationGraphUpdatePayloadValidationFactory factory = new LocationGraphUpdatePayloadValidationFactory(
        new CartesianTraceDateOrderCanonicalizer(),
        List.of(
            new PieGraphPayloadValidator(),
            new IndicatorGraphPayloadValidator(),
            new CartesianGraphPayloadValidator(),
            new TableGraphPayloadValidator(),
            new SunburstGraphPayloadValidator()
        )
    );

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
    void validateForUpdateAcceptsGeneratedDashboardIndicatorPreviewPayloads() {
        Map<String, Object> previewTrace = Map.of(
            "type", "indicator",
            "mode", "gauge+number",
            "value", 72,
            "number", Map.of("suffix", "%"),
            "gauge", Map.of(
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", "#16a34a")
            )
        );

        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(indicatorTrace(68)),
            List.of(previewTrace),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(previewTrace), payload.data());
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
    void validateForUpdateSortsScatterDateSeriesChronologically() {
        Map<String, Object> nextTrace = Map.of(
            "type", "scatter",
            "name", "Cooling Towers",
            "x", List.of("2025-08-01", "2025-07-01", "2025-09-01"),
            "y", List.of(90, 75, 100),
            "customdata", List.of(
                Map.of("sampleCount", 10),
                Map.of("sampleCount", 8),
                Map.of("sampleCount", 12)
            ),
            "marker", Map.of("color", "#1f77b4")
        );

        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(Map.of(
                "type", "scatter",
                "name", "Cooling Towers",
                "x", List.of("2025-08-01", "2025-09-01"),
                "y", List.of(90, 100),
                "customdata", List.of(
                    Map.of("sampleCount", 10),
                    Map.of("sampleCount", 12)
                ),
                "marker", Map.of("color", "#1f77b4")
            )),
            List.of(nextTrace),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(Map.of(
            "type", "scatter",
            "name", "Cooling Towers",
            "x", List.of("2025-07-01", "2025-08-01", "2025-09-01"),
            "y", List.of(75, 90, 100),
            "customdata", List.of(
                Map.of("sampleCount", 8),
                Map.of("sampleCount", 10),
                Map.of("sampleCount", 12)
            ),
            "marker", Map.of("color", "#1f77b4")
        )), payload.data());
    }

    @Test
    void validateForUpdateSortsScatterDateSeriesChronologicallyWhenDatesUseSingleDigitMonthOrDay() {
        Map<String, Object> nextTrace = Map.of(
            "type", "scatter",
            "name", "Cooling Towers",
            "x", List.of("2025-8-1", "2025-07-01", "2025-9-1"),
            "y", List.of(90, 75, 100),
            "customdata", List.of(
                Map.of("sampleCount", 10),
                Map.of("sampleCount", 8),
                Map.of("sampleCount", 12)
            )
        );

        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(Map.of(
                "type", "scatter",
                "name", "Cooling Towers",
                "x", List.of("2025-07-01", "2025-08-01"),
                "y", List.of(75, 90)
            )),
            List.of(nextTrace),
            Map.of()
        );

        assertEquals(List.of(Map.of(
            "type", "scatter",
            "name", "Cooling Towers",
            "x", List.of("2025-07-01", "2025-8-1", "2025-9-1"),
            "y", List.of(75, 90, 100),
            "customdata", List.of(
                Map.of("sampleCount", 8),
                Map.of("sampleCount", 10),
                Map.of("sampleCount", 12)
            )
        )), payload.data());
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
    void validateForUpdateAcceptsTablePayloads() {
        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(tableTrace("Open", 3)),
            List.of(tableTrace("Closed", 7)),
            Map.of("meta", Map.of("aphinitySize", "double"))
        );

        assertEquals(List.of(tableTrace("Closed", 7)), payload.data());
        assertEquals(Map.of("meta", Map.of("aphinitySize", "double")), payload.layout());
    }

    @Test
    void validateForUpdateAcceptsSunburstPayloads() {
        Map<String, Object> nextTrace = sunburstTrace(10, 6);

        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(sunburstTrace(8, 4)),
            List.of(nextTrace),
            Map.of("meta", Map.of("aphinitySize", "duplex"))
        );

        assertEquals(List.of(nextTrace), payload.data());
        assertEquals(Map.of("meta", Map.of("aphinitySize", "duplex")), payload.layout());
    }

    @Test
    void validateForUpdateRejectsMismatchedSunburstNodeArrays() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(sunburstTrace(8, 4)),
                List.of(Map.of(
                    "type", "sunburst",
                    "ids", List.of("site", "site/asset"),
                    "labels", List.of("Site"),
                    "parents", List.of("", "site"),
                    "values", List.of(10, 6)
                )),
                Map.of()
            )
        );
    }

    @Test
    void validateForUpdateRejectsRaggedTableColumns() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(tableTrace("Open", 3)),
                List.of(Map.of(
                    "type", "table",
                    "header", Map.of("values", List.of("Metric", "Value")),
                    "cells", Map.of("values", List.of(
                        List.of("Open", "Closed"),
                        List.of(3)
                    ))
                )),
                Map.of()
            )
        );
    }

    @Test
    void validateForUpdateAcceptsHorizontalBarPayloads() {
        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(horizontalBarTrace(3, 5)),
            List.of(horizontalBarTrace(4, 6)),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(horizontalBarTrace(4, 6)), payload.data());
        assertEquals(Map.of("showlegend", false), payload.layout());
    }

    @Test
    void validateForUpdateAcceptsVerticalBarPayloads() {
        LocationGraphUpdatePayloadValidationFactory.ValidatedGraphPayload payload = factory.validateForUpdate(
            List.of(barTrace(3, 5, 7)),
            List.of(verticalBarTrace(4, 6)),
            Map.of("showlegend", false)
        );

        assertEquals(List.of(verticalBarTrace(4, 6)), payload.data());
        assertEquals(Map.of("showlegend", false), payload.layout());
    }

    @Test
    void validateForUpdateRejectsMismatchedExplicitBarOrientation() {
        assertThrows(IllegalArgumentException.class, () ->
            factory.validateForUpdate(
                List.of(horizontalBarTrace(3, 5)),
                List.of(Map.of(
                    "type", "bar",
                    "orientation", "v",
                    "x", List.of(4, 6),
                    "y", List.of("Jan", "Feb"),
                    "marker", Map.of("color", "#1f77b4")
                )),
                Map.of("showlegend", false)
            )
        );
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
                "bgcolor", "#6b728040",
                "bar", Map.of("color", "#1f77b4"),
                "borderwidth", 0,
                "steps", List.of(
                    Map.of("color", "#6b728040", "range", List.of(0, 100))
                ),
                "threshold", Map.of(
                    "line", Map.of("color", "red", "width", 2),
                    "thickness", 0.75,
                    "value", value
                )
            )
        );
    }

    private Map<String, Object> sunburstTrace(int rootValue, int childValue) {
        return Map.of(
            "type", "sunburst",
            "name", "Conformance",
            "ids", List.of("site", "site/asset"),
            "labels", List.of("Site", "Asset"),
            "parents", List.of("", "site"),
            "values", List.of(rootValue, childValue),
            "branchvalues", "total",
            "insidetextorientation", "radial",
            "marker", Map.of("colors", List.of("#1f77b4", "#16a34a"))
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

    private Map<String, Object> tableTrace(String metric, int value) {
        return Map.of(
            "type", "table",
            "header", Map.of("values", List.of("Metric", "Value")),
            "cells", Map.of("values", List.of(
                List.of(metric),
                List.of(value)
            ))
        );
    }

    private Map<String, Object> horizontalBarTrace(int... values) {
        return Map.of(
            "type", "bar",
            "x", values.length == 0 ? List.of() : toIntegerList(values),
            "y", List.of("Jan", "Feb"),
            "marker", Map.of("color", "#1f77b4"),
            "orientation", "h"
        );
    }

    private Map<String, Object> verticalBarTrace(int... values) {
        return Map.of(
            "type", "bar",
            "x", List.of("Jan", "Feb"),
            "y", values.length == 0 ? List.of() : toIntegerList(values),
            "marker", Map.of("color", "#1f77b4"),
            "orientation", "v"
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
