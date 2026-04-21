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
            List<?> yValues = GraphPayloadValidationSupport.requireListField(trace, "y");
            GraphPayloadValidationSupport.requireNumericList(yValues);

            // Legacy cartesian traces may omit x entirely; keep that shape valid.
            if (trace.containsKey("x")) {
                List<?> xValues = GraphPayloadValidationSupport.requireListField(trace, "x");
                GraphPayloadValidationSupport.requireScalarList(xValues);
            }
        }
    }
}
