package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

final class CartesianGraphPayloadValidator implements LocationGraphUpdateTraceValidator {
    @Override
    public boolean supports(GraphPayloadFamily family) {
        return family == GraphPayloadFamily.CARTESIAN;
    }

    @Override
    public void validate(List<Map<String, Object>> traces, String canonicalTraceType) {
        if (!"bar".equals(canonicalTraceType) && !"scatter".equals(canonicalTraceType)) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        for (Map<String, Object> trace : traces) {
            GraphPayloadValidationSupport.requireCanonicalTraceType(trace, canonicalTraceType);
            if ("bar".equals(canonicalTraceType)) {
                validateBarTrace(trace);
                continue;
            }

            List<?> yValues = GraphPayloadValidationSupport.requireListField(trace, "y");
            GraphPayloadValidationSupport.requireNumericList(yValues);

            // Legacy cartesian traces may omit x entirely; keep that shape valid.
            if (trace.containsKey("x")) {
                List<?> xValues = GraphPayloadValidationSupport.requireListField(trace, "x");
                GraphPayloadValidationSupport.requireScalarList(xValues);
            }
        }
    }

    private void validateBarTrace(Map<String, Object> trace) {
        if (!trace.containsKey("y")) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        List<?> yValues = GraphPayloadValidationSupport.requireListField(trace, "y");
        if (!trace.containsKey("x")) {
            GraphPayloadValidationSupport.requireNumericList(yValues);
            return;
        }

        List<?> xValues = GraphPayloadValidationSupport.requireListField(trace, "x");
        boolean horizontal = canValidateAsHorizontalBarTrace(xValues, yValues);
        if (horizontal) {
            GraphPayloadValidationSupport.requireNumericList(xValues);
            GraphPayloadValidationSupport.requireScalarList(yValues);
            return;
        }

        boolean legacy = canValidateAsLegacyBarTrace(xValues, yValues);
        if (legacy) {
            GraphPayloadValidationSupport.requireScalarList(xValues);
            GraphPayloadValidationSupport.requireNumericList(yValues);
            return;
        }

        throw GraphPayloadValidationSupport.invalidGraphData();
    }

    private boolean canValidateAsHorizontalBarTrace(List<?> xValues, List<?> yValues) {
        try {
            GraphPayloadValidationSupport.requireNumericList(xValues);
            GraphPayloadValidationSupport.requireScalarList(yValues);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean canValidateAsLegacyBarTrace(List<?> xValues, List<?> yValues) {
        try {
            GraphPayloadValidationSupport.requireScalarList(xValues);
            GraphPayloadValidationSupport.requireNumericList(yValues);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
