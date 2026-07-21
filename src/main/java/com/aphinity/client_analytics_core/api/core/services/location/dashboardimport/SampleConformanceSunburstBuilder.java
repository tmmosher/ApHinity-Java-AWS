package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds Plotly sunburst traces from configured sample identity hierarchies. */
final class SampleConformanceSunburstBuilder {
    private static final String DEFAULT_GRAPH_COLOR = "#1f77b4";
    private static final String CONFORMANCE_COLOR = "#16a34a";
    private static final String NON_CONFORMANCE_COLOR = "#dc2626";
    private static final String UNKNOWN_LABEL = "Unknown";

    private SampleConformanceSunburstBuilder() {
    }

    static Map<String, Object> build(
        Map<String, Object> existingTrace,
        String traceName,
        List<LocationDashboardDerivedGraphSupport.HistoricalRawSample> rawSamples,
        List<LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel> hierarchy
    ) {
        SunburstNode root = SunburstNode.root();
        List<LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel> effectiveHierarchy =
            hierarchy == null ? List.of() : hierarchy;
        for (LocationDashboardDerivedGraphSupport.HistoricalRawSample sample
            : rawSamples == null ? List.<LocationDashboardDerivedGraphSupport.HistoricalRawSample>of() : rawSamples) {
            if (sample == null) {
                continue;
            }
            SunburstNode current = root;
            for (int levelIndex = 0; levelIndex < effectiveHierarchy.size(); levelIndex += 1) {
                String label = hierarchyLabel(sample, effectiveHierarchy.get(levelIndex));
                current = current.child(levelIndex, label);
                current.incrementValue();
            }
            current.recordConformance(sample.compliant());
        }

        List<SunburstNode> nodes = new ArrayList<>();
        root.appendDescendants(nodes);
        Map<String, Object> trace = existingTrace == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(existingTrace);
        trace.put("type", "sunburst");
        trace.put("name", traceName == null || traceName.isBlank() ? "Sample Conformance Hierarchy" : traceName.strip());
        trace.put("ids", nodes.stream().map(SunburstNode::id).toList());
        trace.put("labels", nodes.stream().map(SunburstNode::label).toList());
        trace.put("parents", nodes.stream().map(SunburstNode::parentId).toList());
        trace.put("values", nodes.stream().map(SunburstNode::value).toList());
        trace.put("branchvalues", "total");
        trace.put("sort", false);
        trace.put("hovertemplate", "%{label}: %{value}<extra></extra>");
        Map<String, Object> marker = copyMap(trace.get("marker"));
        marker.put("colors", nodes.stream().map(SunburstNode::color).toList());
        trace.put("marker", marker);
        return trace;
    }

    private static String hierarchyLabel(
        LocationDashboardDerivedGraphSupport.HistoricalRawSample sample,
        LocationDashboardImportStrategyConfig.DerivedGraphHierarchyLevel level
    ) {
        if (level == null || level.source() == null) {
            return UNKNOWN_LABEL;
        }
        String value = switch (level.source()) {
            case IDENTITY -> sample.identityValues().get(level.key());
            case MEASUREMENT -> sample.measurementName();
        };
        if (value == null || value.isBlank()) {
            return UNKNOWN_LABEL;
        }
        return value.strip();
    }

    private static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        rawMap.forEach((key, entry) -> {
            if (key != null) {
                copy.put(String.valueOf(key), entry);
            }
        });
        return copy;
    }

    private static final class SunburstNode {
        private static final Comparator<SunburstNode> NODE_ORDER = Comparator
            .comparing(SunburstNode::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SunburstNode::label);

        private final String id;
        private final String label;
        private final String parentId;
        private final String color;
        private final Map<String, SunburstNode> children = new LinkedHashMap<>();
        private long value;
        private long conformances;
        private long nonConformances;

        private SunburstNode(String id, String label, String parentId, String color) {
            this.id = id;
            this.label = label;
            this.parentId = parentId;
            this.color = color;
        }

        static SunburstNode root() {
            return new SunburstNode("", "", "", DEFAULT_GRAPH_COLOR);
        }

        SunburstNode child(int levelIndex, String childLabel) {
            String childKey = levelIndex + "\u0000" + childLabel;
            return children.computeIfAbsent(childKey, ignored -> new SunburstNode(
                pathId(id, levelIndex, childLabel), childLabel, id, DEFAULT_GRAPH_COLOR
            ));
        }

        void incrementValue() {
            value += 1L;
        }

        void recordConformance(boolean compliant) {
            if (compliant) {
                conformances += 1L;
            } else {
                nonConformances += 1L;
            }
        }

        void appendDescendants(List<SunburstNode> destination) {
            children.values().stream().sorted(NODE_ORDER).forEach(child -> {
                destination.add(child);
                child.appendDescendants(destination);
                if (child.children.isEmpty()) {
                    destination.add(statusNode(child, "Conformances", child.conformances, CONFORMANCE_COLOR));
                    destination.add(statusNode(child, "Non-Conformances", child.nonConformances, NON_CONFORMANCE_COLOR));
                }
            });
        }

        private static SunburstNode statusNode(SunburstNode parent, String label, long value, String color) {
            SunburstNode node = new SunburstNode(
                pathId(parent.id, Integer.MAX_VALUE, label), label, parent.id, color
            );
            node.value = value;
            return node;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        String parentId() {
            return parentId;
        }

        long value() {
            return value;
        }

        String color() {
            return color;
        }

        private static String pathId(String parentId, int levelIndex, String label) {
            return parentId + "/" + levelIndex + ":" + label.length() + ":" + label;
        }
    }
}
