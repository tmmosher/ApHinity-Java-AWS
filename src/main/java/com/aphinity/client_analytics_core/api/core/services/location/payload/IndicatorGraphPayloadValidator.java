package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

import static com.aphinity.client_analytics_core.api.core.services.location.payload.GraphPayloadValidationSupport.invalidGraphData;

final class IndicatorGraphPayloadValidator implements LocationGraphUpdateTraceValidator {
    private static final double VALUE_MIN = 0.0;
    private static final double VALUE_MAX = 100.0;

    @Override
    public boolean supports(GraphPayloadFamily family) {
        return family == GraphPayloadFamily.INDICATOR;
    }

    @Override
    public void validate(List<Map<String, Object>> traces, String canonicalTraceType) {
        if (!"indicator".equals(canonicalTraceType) || traces.size() != 1) {
            throw invalidGraphData();
        }

        Map<String, Object> trace = traces.getFirst();
        GraphPayloadValidationSupport.requireCanonicalTraceType(trace, canonicalTraceType);
        String mode = GraphPayloadValidationSupport.requireStringField(trace, "mode");
        if (!"gauge+number".equals(mode)) {
            throw invalidGraphData();
        }

        Number value = GraphPayloadValidationSupport.requireNumberField(trace, "value");
        if (value.doubleValue() < VALUE_MIN || value.doubleValue() > VALUE_MAX) {
            throw invalidGraphData();
        }

        Map<String, Object> number = GraphPayloadValidationSupport.requireObjectField(trace, "number");
        if (!"%".equals(GraphPayloadValidationSupport.requireStringField(number, "suffix"))) {
            throw invalidGraphData();
        }
        Map<String, Object> font = GraphPayloadValidationSupport.requireObjectField(number, "font");
        GraphPayloadValidationSupport.requireNumberField(font, "size");

        Map<String, Object> gauge = GraphPayloadValidationSupport.requireObjectField(trace, "gauge");
        if (!"angular".equals(GraphPayloadValidationSupport.requireStringField(gauge, "shape"))) {
            throw invalidGraphData();
        }

        Map<String, Object> axis = GraphPayloadValidationSupport.requireObjectField(gauge, "axis");
        List<?> range = GraphPayloadValidationSupport.requireListField(axis, "range");
        if (range.size() != 2) {
            throw invalidGraphData();
        }
        if (!(range.get(0) instanceof Number rangeMin)
            || !(range.get(1) instanceof Number rangeMax)
            || !GraphPayloadValidationSupport.numericValueEquals(rangeMin, VALUE_MIN)
            || !GraphPayloadValidationSupport.numericValueEquals(rangeMax, VALUE_MAX)) {
            throw invalidGraphData();
        }

        Map<String, Object> bar = GraphPayloadValidationSupport.requireObjectField(gauge, "bar");
        GraphPayloadValidationSupport.requireStringField(bar, "color");
    }
}
