package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

final class TableGraphPayloadValidator implements LocationGraphUpdateTraceValidator {
    @Override
    public boolean supports(GraphPayloadFamily family) {
        return family == GraphPayloadFamily.TABLE;
    }

    @Override
    public void validate(List<Map<String, Object>> traces, String canonicalTraceType) {
        if (!"table".equals(canonicalTraceType) || traces.size() != 1) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        Map<String, Object> trace = traces.getFirst();
        GraphPayloadValidationSupport.requireCanonicalTraceType(trace, canonicalTraceType);
        Map<String, Object> header = GraphPayloadValidationSupport.requireObjectField(trace, "header");
        List<?> headers = GraphPayloadValidationSupport.requireListField(header, "values");
        GraphPayloadValidationSupport.requireScalarList(headers);

        Map<String, Object> cells = GraphPayloadValidationSupport.requireObjectField(trace, "cells");
        List<?> columns = GraphPayloadValidationSupport.requireListField(cells, "values");
        if (headers.size() != columns.size()) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        Integer rowCount = null;
        for (Object rawColumn : columns) {
            if (!(rawColumn instanceof List<?> column)) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
            if (rowCount == null) {
                rowCount = column.size();
            } else if (rowCount != column.size()) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
            for (Object value : column) {
                GraphPayloadValidationSupport.requireScalarValue(value);
            }
        }
    }
}
