package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

final class PieGraphPayloadValidator implements LocationGraphUpdateTraceValidator {
    @Override
    public boolean supports(GraphPayloadFamily family) {
        return family == GraphPayloadFamily.PIE;
    }

    @Override
    public void validate(List<Map<String, Object>> traces, String canonicalTraceType) {
        if (!"pie".equals(canonicalTraceType) || traces.size() != 1) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        for (Map<String, Object> trace : traces) {
            GraphPayloadValidationSupport.requireCanonicalTraceType(trace, canonicalTraceType);
            List<?> labels = GraphPayloadValidationSupport.requireListField(trace, "labels");
            List<?> values = GraphPayloadValidationSupport.requireListField(trace, "values");
            if (labels.size() != values.size()) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
            GraphPayloadValidationSupport.requireStringList(labels);
            GraphPayloadValidationSupport.requireNumericList(values);
        }
    }
}
