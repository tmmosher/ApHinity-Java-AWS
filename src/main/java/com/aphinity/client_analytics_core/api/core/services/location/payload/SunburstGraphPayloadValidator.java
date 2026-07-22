package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SunburstGraphPayloadValidator implements LocationGraphUpdateTraceValidator {
    @Override
    public boolean supports(GraphPayloadFamily family) {
        return family == GraphPayloadFamily.SUNBURST;
    }

    @Override
    public void validate(List<Map<String, Object>> traces, String canonicalTraceType) {
        if (!"sunburst".equals(canonicalTraceType) || traces.size() != 1) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        Map<String, Object> trace = traces.getFirst();
        GraphPayloadValidationSupport.requireCanonicalTraceType(trace, canonicalTraceType);
        List<?> ids = GraphPayloadValidationSupport.requireListField(trace, "ids");
        List<?> labels = GraphPayloadValidationSupport.requireListField(trace, "labels");
        List<?> parents = GraphPayloadValidationSupport.requireListField(trace, "parents");
        List<?> values = GraphPayloadValidationSupport.requireListField(trace, "values");
        int nodeCount = ids.size();
        if (labels.size() != nodeCount || parents.size() != nodeCount || values.size() != nodeCount) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }

        GraphPayloadValidationSupport.requireStringList(ids);
        GraphPayloadValidationSupport.requireStringList(labels);
        GraphPayloadValidationSupport.requireStringList(parents);
        GraphPayloadValidationSupport.requireNumericList(values);
        validateHierarchy(ids, parents);
        validateMarkerColors(trace, nodeCount);
    }

    private void validateHierarchy(List<?> ids, List<?> parents) {
        Set<String> nodeIds = new HashSet<>();
        for (Object rawId : ids) {
            String id = (String) rawId;
            if (id.isBlank() || !nodeIds.add(id)) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
        }

        for (int index = 0; index < parents.size(); index++) {
            String parent = (String) parents.get(index);
            if ((!parent.isEmpty() && !nodeIds.contains(parent)) || parent.equals(ids.get(index))) {
                throw GraphPayloadValidationSupport.invalidGraphData();
            }
        }
    }

    private void validateMarkerColors(Map<String, Object> trace, int nodeCount) {
        if (!trace.containsKey("marker")) {
            return;
        }
        Map<String, Object> marker = GraphPayloadValidationSupport.requireObjectField(trace, "marker");
        if (!marker.containsKey("colors")) {
            return;
        }
        List<?> colors = GraphPayloadValidationSupport.requireListField(marker, "colors");
        if (colors.size() != nodeCount) {
            throw GraphPayloadValidationSupport.invalidGraphData();
        }
        GraphPayloadValidationSupport.requireStringList(colors);
    }
}
