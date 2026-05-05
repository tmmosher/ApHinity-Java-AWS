package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

final class LocationDashboardDerivedGraphSupport {
    private static final String DEFAULT_GRAPH_COLOR = "#1f77b4";
    private static final String ACTIVE_GRAPH_COLOR = "#dc2626";
    private static final String RESOLVED_GRAPH_COLOR = "#16a34a";
    private static final String KPI_REMAINDER_COLOR = "rgba(15, 23, 42, 0.12)";

    private LocationDashboardDerivedGraphSupport() {
    }

    static List<DerivedGraphUpdate> buildUpdates(
        Collection<Graph> assignedGraphs,
        List<LocationDashboardImportStrategy.ImportedObservation> observations,
        List<CorrectiveActionState> correctiveActionStates
    ) {
        List<DerivedGraphUpdate> updates = new ArrayList<>();
        for (Graph graph : assignedGraphs) {
            Optional<DerivedGraphType> derivedGraphType = classify(graph);
            if (derivedGraphType.isEmpty()) {
                continue;
            }
            updates.add(new DerivedGraphUpdate(
                graph,
                derivedGraphType.get(),
                buildPayload(derivedGraphType.get(), graph, observations, correctiveActionStates)
            ));
        }
        return List.copyOf(updates);
    }

    private static Optional<DerivedGraphType> classify(Graph graph) {
        if (graph == null || isClearlyTestingGraphName(graph.getName())) {
            return Optional.empty();
        }

        String metadataType = readDerivedGraphTypeMetadata(graph);
        if (metadataType != null) {
            return DerivedGraphType.fromMetadataValue(metadataType);
        }

        String normalizedName = normalizeKey(graph.getName());
        String normalizedTitle = normalizeKey(readLayoutTitleText(graph));
        String normalizedGraphType = normalizeKey(resolveGraphType(graph));

        if (Objects.equals(normalizedGraphType, "pie")) {
            if (Objects.equals(normalizedName, "total number of samples")) {
                return Optional.of(DerivedGraphType.TOTAL_SAMPLES);
            }
            if (Objects.equals(normalizedName, "total non-conformances")) {
                return Optional.of(DerivedGraphType.TOTAL_NON_CONFORMANCES);
            }
            if (Objects.equals(normalizedName, "active non-conformance percent")) {
                return Optional.of(DerivedGraphType.ACTIVE_NON_CONFORMANCE_PERCENT);
            }
            if (Objects.equals(normalizedName, "percent conformance")
                || Objects.equals(normalizedName, "conformance percent")
                || Objects.equals(normalizedName, "compliance percent")) {
                return Optional.of(DerivedGraphType.PERCENT_CONFORMANCE);
            }
            if (Objects.equals(normalizedName, "percent resolved")
                || Objects.equals(normalizedName, "resolution percent")) {
                return Optional.of(DerivedGraphType.PERCENT_RESOLVED);
            }
        }

        if (Objects.equals(normalizedGraphType, "indicator")) {
            if (Objects.equals(normalizedName, "percent conformance")
                || Objects.equals(normalizedName, "conformance percent")
                || Objects.equals(normalizedName, "compliance percent")) {
                return Optional.of(DerivedGraphType.PERCENT_CONFORMANCE);
            }
            if (Objects.equals(normalizedName, "percent resolved")
                || Objects.equals(normalizedName, "resolution percent")) {
                return Optional.of(DerivedGraphType.PERCENT_RESOLVED);
            }
        }

        if (Objects.equals(normalizedGraphType, "bar")) {
            if (Objects.equals(normalizedName, "non-conformances")) {
                if (Objects.equals(normalizedTitle, "by facility")) {
                    return Optional.of(DerivedGraphType.NON_CONFORMANCES_BY_FACILITY);
                }
                if (Objects.equals(normalizedTitle, "by water system type")) {
                    return Optional.of(DerivedGraphType.NON_CONFORMANCES_BY_SYSTEM_TYPE);
                }
                if (Objects.equals(normalizedTitle, "by water quality category")) {
                    return Optional.of(DerivedGraphType.NON_CONFORMANCES_BY_CATEGORY);
                }
            }
            if (Objects.equals(normalizedName, "non-conformance status")) {
                if (Objects.equals(normalizedTitle, "by facility")) {
                    return Optional.of(DerivedGraphType.NON_CONFORMANCE_STATUS_BY_FACILITY);
                }
                if (Objects.equals(normalizedTitle, "turnaround time")) {
                    return Optional.of(DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME);
                }
            }
        }

        return Optional.empty();
    }

    static String metadataValue(DerivedGraphType derivedGraphType) {
        return derivedGraphType.metadataValue();
    }

    private static List<Map<String, Object>> buildPayload(
        DerivedGraphType derivedGraphType,
        Graph graph,
        List<LocationDashboardImportStrategy.ImportedObservation> observations,
        List<CorrectiveActionState> correctiveActionStates
    ) {
        long totalSamples = observations.size();
        long compliantSamples = observations.stream().filter(LocationDashboardImportStrategy.ImportedObservation::compliant).count();
        long totalNonConformances = correctiveActionStates.size();
        long resolvedNonConformances = correctiveActionStates.stream().filter(CorrectiveActionState::resolved).count();
        long activeNonConformances = totalNonConformances - resolvedNonConformances;

        return switch (derivedGraphType) {
            case TOTAL_SAMPLES -> List.of(buildPieTrace(
                graph,
                "Samples",
                List.of("Total Samples"),
                List.of(totalSamples),
                List.of(DEFAULT_GRAPH_COLOR)
            ));
            case TOTAL_NON_CONFORMANCES -> List.of(buildPieTrace(
                graph,
                "Non-Conformances",
                List.of("Total Non-Conformances"),
                List.of(totalNonConformances),
                List.of(ACTIVE_GRAPH_COLOR)
            ));
            case ACTIVE_NON_CONFORMANCE_PERCENT -> {
                long activePercent = calculateRoundedPercent(activeNonConformances, totalNonConformances);
                yield List.of(buildPieTrace(
                    graph,
                    "Active Non-Conformances",
                    List.of("Active", "Resolved"),
                    List.of(activePercent, Math.max(0L, 100L - activePercent)),
                    List.of(ACTIVE_GRAPH_COLOR, KPI_REMAINDER_COLOR)
                ));
            }
            case PERCENT_CONFORMANCE -> {
                long percentConformance = calculateRoundedPercent(compliantSamples, totalSamples);
                yield buildPercentPayload(graph, "Conformance", percentConformance, DEFAULT_GRAPH_COLOR);
            }
            case PERCENT_RESOLVED -> {
                long percentResolved = calculateRoundedPercent(resolvedNonConformances, totalNonConformances);
                yield buildPercentPayload(graph, "Resolved", percentResolved, RESOLVED_GRAPH_COLOR);
            }
            case NON_CONFORMANCES_BY_FACILITY -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countLabels(correctiveActionStates, state -> state.draft().facilityName()),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCES_BY_SYSTEM_TYPE -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countLabels(correctiveActionStates, state -> state.draft().systemTypeName()),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCES_BY_CATEGORY -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countLabels(correctiveActionStates, state -> state.draft().measurementName()),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCE_STATUS_BY_FACILITY -> buildStatusByFacilityPayload(graph, correctiveActionStates);
            case NON_CONFORMANCE_TURNAROUND_TIME -> List.of(buildHorizontalBarTrace(
                graph,
                "Resolved",
                countLabelsByTurnaround(correctiveActionStates),
                RESOLVED_GRAPH_COLOR,
                0
            ));
        };
    }

    private static List<Map<String, Object>> buildPercentPayload(
        Graph graph,
        String traceName,
        long percent,
        String color
    ) {
        String normalizedGraphType = normalizeKey(resolveGraphType(graph));
        if (Objects.equals(normalizedGraphType, "indicator")) {
            return List.of(buildIndicatorTrace(graph, traceName, percent, color));
        }
        return List.of(buildPieTrace(
            graph,
            traceName,
            List.of(traceName, "Remaining"),
            List.of(percent, Math.max(0L, 100L - percent)),
            List.of(color, KPI_REMAINDER_COLOR)
        ));
    }

    private static List<Map<String, Object>> buildStatusByFacilityPayload(
        Graph graph,
        List<CorrectiveActionState> correctiveActionStates
    ) {
        Map<String, long[]> countsByFacility = new LinkedHashMap<>();
        for (CorrectiveActionState correctiveActionState : correctiveActionStates) {
            String facilityName = normalizeLabel(correctiveActionState.draft().facilityName());
            if (facilityName == null) {
                continue;
            }
            long[] counts = countsByFacility.computeIfAbsent(facilityName, ignored -> new long[2]);
            if (correctiveActionState.resolved()) {
                counts[1] += 1;
            } else {
                counts[0] += 1;
            }
        }

        List<Map.Entry<String, long[]>> orderedCounts = countsByFacility.entrySet().stream()
            .sorted(Comparator
                .<Map.Entry<String, long[]>>comparingLong(entry -> -(entry.getValue()[0] + entry.getValue()[1]))
                .thenComparing(Map.Entry::getKey))
            .toList();

        List<String> facilityLabels = orderedCounts.stream()
            .map(Map.Entry::getKey)
            .toList();
        List<Long> activeCounts = orderedCounts.stream()
            .map(entry -> entry.getValue()[0])
            .toList();
        List<Long> resolvedCounts = orderedCounts.stream()
            .map(entry -> entry.getValue()[1])
            .toList();

        return List.of(
            buildHorizontalBarTrace(graph, "Active", facilityLabels, activeCounts, ACTIVE_GRAPH_COLOR, 0),
            buildHorizontalBarTrace(graph, "Resolved", facilityLabels, resolvedCounts, RESOLVED_GRAPH_COLOR, 1)
        );
    }

    private static Map<String, Long> countLabels(
        List<CorrectiveActionState> correctiveActionStates,
        Function<CorrectiveActionState, String> labelExtractor
    ) {
        return correctiveActionStates.stream()
            .map(labelExtractor)
            .map(LocationDashboardDerivedGraphSupport::normalizeLabel)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                Function.identity(),
                LinkedHashMap::new,
                Collectors.counting()
            ))
            .entrySet().stream()
            .sorted(Comparator
                .<Map.Entry<String, Long>>comparingLong(entry -> -entry.getValue())
                .thenComparing(Map.Entry::getKey))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private static Map<String, Long> countLabelsByTurnaround(List<CorrectiveActionState> correctiveActionStates) {
        Map<String, Long> countsByTurnaround = new LinkedHashMap<>();
        correctiveActionStates.stream()
            .filter(CorrectiveActionState::resolved)
            .map(LocationDashboardDerivedGraphSupport::turnaroundLabel)
            .filter(Objects::nonNull)
            .forEach(label -> countsByTurnaround.merge(label, 1L, Long::sum));

        return countsByTurnaround.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> parseLeadingInteger(entry.getKey())))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private static String turnaroundLabel(CorrectiveActionState correctiveActionState) {
        LocalDate observedDate = correctiveActionState.draft().observedDate();
        ServiceEvent serviceEvent = correctiveActionState.serviceEvent();
        if (observedDate == null || serviceEvent == null || serviceEvent.getEndEventDate() == null) {
            return null;
        }
        LocalDateTime observedAt = LocalDateTime.of(observedDate, LocalTime.MIDNIGHT);
        LocalDateTime resolvedAt = LocalDateTime.of(
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime() == null ? LocalTime.MIDNIGHT : serviceEvent.getEndEventTime()
        );
        long turnaroundDays = Math.max(0L, ChronoUnit.DAYS.between(observedAt, resolvedAt));
        return turnaroundDays == 1L ? "1 day" : turnaroundDays + " days";
    }

    private static int parseLeadingInteger(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        int index = value.indexOf(' ');
        String numericPortion = index >= 0 ? value.substring(0, index) : value;
        try {
            return Integer.parseInt(numericPortion);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static Map<String, Object> buildPieTrace(
        Graph graph,
        String traceName,
        List<String> labels,
        List<? extends Number> values,
        List<String> colors
    ) {
        Map<String, Object> trace = piePrototype(graph, traceName);
        trace.put("labels", List.copyOf(labels));
        trace.put("values", List.copyOf(values));
        trace.put("marker", Map.of(
            "color", colors.isEmpty() ? DEFAULT_GRAPH_COLOR : colors.getFirst(),
            "colors", List.copyOf(colors)
        ));
        return trace;
    }

    private static Map<String, Object> buildIndicatorTrace(
        Graph graph,
        String traceName,
        long value,
        String color
    ) {
        Map<String, Object> trace = indicatorPrototype(graph, traceName);
        trace.put("value", value);
        Map<String, Object> gauge = copyMap(trace.get("gauge"));
        Map<String, Object> bar = copyMap(gauge.get("bar"));
        bar.put("color", color);
        gauge.put("bar", bar);
        trace.put("gauge", gauge);
        return trace;
    }

    private static Map<String, Object> buildHorizontalBarTrace(
        Graph graph,
        String traceName,
        Map<String, Long> countsByLabel,
        String color,
        int traceIndex
    ) {
        List<String> labels = new ArrayList<>(countsByLabel.keySet());
        List<Long> values = labels.stream()
            .map(countsByLabel::get)
            .toList();
        return buildHorizontalBarTrace(graph, traceName, labels, values, color, traceIndex);
    }

    private static Map<String, Object> buildHorizontalBarTrace(
        Graph graph,
        String traceName,
        List<String> labels,
        List<Long> values,
        String color,
        int traceIndex
    ) {
        Map<String, Object> trace = barPrototype(graph, traceName, traceIndex);
        trace.put("x", List.copyOf(values));
        trace.put("y", List.copyOf(labels));
        Map<String, Object> marker = copyMap(trace.get("marker"));
        marker.put("color", color);
        trace.put("marker", marker);
        return trace;
    }

    private static Map<String, Object> piePrototype(Graph graph, String traceName) {
        Map<String, Object> trace = copyMap(findExistingTrace(graph, "pie", 0));
        if (trace.isEmpty()) {
            trace.put("hole", 0.72);
            trace.put("sort", false);
            trace.put("textinfo", "none");
            trace.put("direction", "clockwise");
            trace.put("hovertemplate", "%{label}: %{value}<extra></extra>");
        }
        trace.put("type", "pie");
        trace.put("name", traceName);
        return trace;
    }

    private static Map<String, Object> indicatorPrototype(Graph graph, String traceName) {
        Map<String, Object> trace = copyMap(findExistingTrace(graph, "indicator", 0));
        if (trace.isEmpty()) {
            trace.put("mode", "gauge+number");
            trace.put("number", Map.of(
                "suffix", "%",
                "font", Map.of("size", 22)
            ));
            trace.put("gauge", Map.of(
                "shape", "angular",
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", DEFAULT_GRAPH_COLOR),
                "borderwidth", 0
            ));
        }
        trace.put("type", "indicator");
        trace.put("name", traceName);
        return trace;
    }

    private static Map<String, Object> barPrototype(Graph graph, String traceName, int traceIndex) {
        Map<String, Object> trace = copyMap(findExistingTrace(graph, "bar", traceIndex));
        if (trace.isEmpty()) {
            trace.put("orientation", "h");
            trace.put("marker", Map.of("color", DEFAULT_GRAPH_COLOR));
        }
        trace.put("type", "bar");
        trace.put("name", traceName);
        trace.put("orientation", "h");
        return trace;
    }

    private static Map<String, Object> findExistingTrace(Graph graph, String expectedType, int traceIndex) {
        List<Map<String, Object>> currentData = currentTraceList(graph);
        if (traceIndex >= 0 && traceIndex < currentData.size()) {
            Map<String, Object> indexedTrace = currentData.get(traceIndex);
            if (Objects.equals(normalizeKey(String.valueOf(indexedTrace.get("type"))), normalizeKey(expectedType))) {
                return indexedTrace;
            }
        }
        for (Map<String, Object> trace : currentData) {
            if (Objects.equals(normalizeKey(String.valueOf(trace.get("type"))), normalizeKey(expectedType))) {
                return trace;
            }
        }
        return Map.of();
    }

    private static List<Map<String, Object>> currentTraceList(Graph graph) {
        if (graph == null) {
            return List.of();
        }
        try {
            return GraphPayloadMapper.toTraceList(graph.getData());
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        mapValue.forEach((key, entry) -> {
            if (key != null) {
                copy.put(String.valueOf(key), entry);
            }
        });
        return copy;
    }

    private static long calculateRoundedPercent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0L;
        }
        return Math.round((numerator * 100.0d) / denominator);
    }

    private static String readLayoutTitleText(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return null;
        }
        Object title = graph.getLayout().get("title");
        if (title instanceof String stringTitle) {
            return stringTitle;
        }
        if (title instanceof Map<?, ?> titleMap) {
            Object text = titleMap.get("text");
            if (text instanceof String stringText) {
                return stringText;
            }
        }
        return null;
    }

    private static String resolveGraphType(Graph graph) {
        if (graph == null) {
            return null;
        }
        String graphType = normalizeKey(graph.getGraphType());
        if (graphType != null) {
            return graphType;
        }
        List<Map<String, Object>> currentData = currentTraceList(graph);
        if (currentData.isEmpty()) {
            return null;
        }
        Object traceType = currentData.getFirst().get("type");
        return traceType == null ? null : String.valueOf(traceType);
    }

    private static String readDerivedGraphTypeMetadata(Graph graph) {
        if (graph == null || graph.getLayout() == null) {
            return null;
        }
        Object meta = graph.getLayout().get("meta");
        if (!(meta instanceof Map<?, ?> metaMap)) {
            return null;
        }
        Object importMeta = metaMap.get("aphinityImport");
        if (!(importMeta instanceof Map<?, ?> importMetaMap)) {
            return null;
        }
        Object derivedGraphType = importMetaMap.get("derivedGraphType");
        return derivedGraphType == null ? null : String.valueOf(derivedGraphType);
    }

    private static boolean isClearlyTestingGraphName(String graphName) {
        String normalizedName = normalizeKey(graphName);
        if (normalizedName == null) {
            return false;
        }
        return normalizedName.contains("test")
            || normalizedName.contains("renaming")
            || normalizedName.contains("i'm renaming")
            || normalizedName.contains("new plot graph")
            || Objects.equals(normalizedName, "beats");
    }

    private static String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    record CorrectiveActionState(
        LocationDashboardImportStrategy.CorrectiveActionDraft draft,
        ServiceEvent serviceEvent
    ) {
        boolean resolved() {
            return serviceEvent != null && serviceEvent.getStatus() == ServiceEventStatus.COMPLETED;
        }
    }

    record DerivedGraphUpdate(
        Graph graph,
        DerivedGraphType derivedGraphType,
        List<Map<String, Object>> data
    ) {
    }

    enum DerivedGraphType {
        TOTAL_SAMPLES("total_samples"),
        TOTAL_NON_CONFORMANCES("total_non_conformances"),
        ACTIVE_NON_CONFORMANCE_PERCENT("active_non_conformance_percent"),
        PERCENT_CONFORMANCE("percent_conformance"),
        PERCENT_RESOLVED("percent_resolved"),
        NON_CONFORMANCES_BY_FACILITY("non_conformances_by_facility"),
        NON_CONFORMANCES_BY_SYSTEM_TYPE("non_conformances_by_system_type"),
        NON_CONFORMANCES_BY_CATEGORY("non_conformances_by_category"),
        NON_CONFORMANCE_STATUS_BY_FACILITY("non_conformance_status_by_facility"),
        NON_CONFORMANCE_TURNAROUND_TIME("non_conformance_turnaround_time");

        private final String metadataValue;

        DerivedGraphType(String metadataValue) {
            this.metadataValue = metadataValue;
        }

        String metadataValue() {
            return metadataValue;
        }

        static Optional<DerivedGraphType> fromMetadataValue(String rawValue) {
            String normalized = normalizeKey(rawValue);
            if (normalized == null) {
                return Optional.empty();
            }
            for (DerivedGraphType value : values()) {
                if (Objects.equals(value.metadataValue, normalized)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }
}
