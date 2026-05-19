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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class LocationDashboardDerivedGraphSupport {
    private static final String DEFAULT_GRAPH_COLOR = "#1f77b4";
    private static final String ACTIVE_GRAPH_COLOR = "#dc2626";
    private static final String RESOLVED_GRAPH_COLOR = "#16a34a";
    private static final String KPI_REMAINDER_COLOR = "rgba(15, 23, 42, 0.12)";
    private static final String UNKNOWN_FACILITY_LABEL = "Unknown Facility";
    private static final String UNKNOWN_SYSTEM_TYPE_LABEL = "Unknown System Type";
    private static final String UNKNOWN_CATEGORY_LABEL = "Unknown Category";
    private static final List<String> TURNAROUND_BUCKETS = List.of("< 3 days", "< 1 week", "< 1 month", "< 3 months");

    private LocationDashboardDerivedGraphSupport() {
    }

    static String metadataValue(LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType) {
        return derivedGraphType == null ? null : derivedGraphType.value();
    }

    static List<Map<String, Object>> buildPayload(
        LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedGraphDefinition,
        Graph graph,
        HistoricalDerivedData historicalData
    ) {
        LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType = derivedGraphDefinition.derivedType();
        List<HistoricalSamplePoint> waterQualitySamplePoints = historicalData.allSamplePoints().stream()
            .filter(samplePoint -> samplePoint.measurementName() != null)
            .toList();
        List<HistoricalSamplePoint> systemTypeSamplePoints = historicalData.allSamplePoints().stream()
            .filter(samplePoint -> samplePoint.systemTypeName() != null)
            .toList();
        List<HistoricalCorrectiveAction> correctiveActions = historicalData.correctiveActions();

        long totalSamples = waterQualitySamplePoints.stream().mapToLong(HistoricalSamplePoint::sampleCount).sum();
        long compliantSamples = waterQualitySamplePoints.stream().mapToLong(HistoricalSamplePoint::compliantCount).sum();
        long totalSampleNonConformances = waterQualitySamplePoints.stream()
            .mapToLong(HistoricalSamplePoint::nonConformingCount)
            .sum();
        long totalIncidentNonConformances = correctiveActions.size();
        long resolvedNonConformances = correctiveActions.stream().filter(HistoricalCorrectiveAction::resolved).count();
        long activeNonConformances = totalIncidentNonConformances - resolvedNonConformances;

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
                List.of(totalSampleNonConformances),
                List.of(ACTIVE_GRAPH_COLOR)
            ));
            case ACTIVE_NON_CONFORMANCE_PERCENT -> {
                long activePercent = calculateRoundedPercent(activeNonConformances, totalIncidentNonConformances);
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
            case NON_CONFORMANCE_COUNT -> buildCountPayload(
                graph,
                "Non-Conformances",
                totalSampleNonConformances,
                ACTIVE_GRAPH_COLOR
            );
            case PERCENT_RESOLVED -> {
                long percentResolved = calculateRoundedPercent(resolvedNonConformances, totalIncidentNonConformances);
                yield buildPercentPayload(graph, "Resolved", percentResolved, RESOLVED_GRAPH_COLOR);
            }
            case NON_CONFORMANCES_BY_FACILITY -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countSampleLabels(waterQualitySamplePoints, HistoricalSamplePoint::facilityName, UNKNOWN_FACILITY_LABEL),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCES_BY_SYSTEM_TYPE -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countSampleLabels(systemTypeSamplePoints, HistoricalSamplePoint::systemTypeName, UNKNOWN_SYSTEM_TYPE_LABEL),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCES_BY_CATEGORY -> List.of(buildHorizontalBarTrace(
                graph,
                "Non-Conformances",
                countSampleLabels(waterQualitySamplePoints, HistoricalSamplePoint::measurementName, UNKNOWN_CATEGORY_LABEL),
                DEFAULT_GRAPH_COLOR,
                0
            ));
            case NON_CONFORMANCE_STATUS_BY_FACILITY -> buildStatusByFacilityPayload(graph, correctiveActions);
            case NON_CONFORMANCE_TURNAROUND_TIME -> List.of(buildHorizontalBarTrace(
                graph,
                "Resolved",
                countLabelsByTurnaround(correctiveActions),
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

    private static List<Map<String, Object>> buildCountPayload(
        Graph graph,
        String traceName,
        long count,
        String color
    ) {
        String normalizedGraphType = normalizeKey(resolveGraphType(graph));
        if (Objects.equals(normalizedGraphType, "indicator")) {
            return List.of(buildCountIndicatorTrace(graph, traceName, count, color));
        }
        return List.of(buildPieTrace(
            graph,
            traceName,
            List.of(traceName),
            List.of(count),
            List.of(color)
        ));
    }

    private static List<Map<String, Object>> buildStatusByFacilityPayload(
        Graph graph,
        List<HistoricalCorrectiveAction> correctiveActions
    ) {
        Map<String, long[]> countsByFacility = new LinkedHashMap<>();
        for (HistoricalCorrectiveAction correctiveAction : correctiveActions) {
            // Historical corrective actions can predate structured import metadata. Keep
            // those incidents visible instead of silently dropping them from grouped totals.
            String facilityName = labelOrFallback(correctiveAction.facilityName(), UNKNOWN_FACILITY_LABEL);
            long[] counts = countsByFacility.computeIfAbsent(facilityName, ignored -> new long[2]);
            if (correctiveAction.resolved()) {
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

        List<String> facilityLabels = orderedCounts.stream().map(Map.Entry::getKey).toList();
        List<Long> activeCounts = orderedCounts.stream().map(entry -> entry.getValue()[0]).toList();
        List<Long> resolvedCounts = orderedCounts.stream().map(entry -> entry.getValue()[1]).toList();

        return List.of(
            buildHorizontalBarTrace(graph, "Active", facilityLabels, activeCounts, ACTIVE_GRAPH_COLOR, 0),
            buildHorizontalBarTrace(graph, "Resolved", facilityLabels, resolvedCounts, RESOLVED_GRAPH_COLOR, 1)
        );
    }

    private static Map<String, Long> countLabels(
        List<HistoricalCorrectiveAction> correctiveActions,
        Function<HistoricalCorrectiveAction, String> labelExtractor,
        String fallbackLabel
    ) {
        return correctiveActions.stream()
            .map(labelExtractor)
            .map(label -> labelOrFallback(label, fallbackLabel))
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

    private static Map<String, Long> countSampleLabels(
        List<HistoricalSamplePoint> samplePoints,
        Function<HistoricalSamplePoint, String> labelExtractor,
        String fallbackLabel
    ) {
        Map<String, Long> countsByLabel = new LinkedHashMap<>();
        for (HistoricalSamplePoint samplePoint : samplePoints) {
            if (samplePoint == null || samplePoint.nonConformingCount() <= 0L) {
                continue;
            }
            String label = labelOrFallback(labelExtractor.apply(samplePoint), fallbackLabel);
            countsByLabel.merge(label, samplePoint.nonConformingCount(), Long::sum);
        }

        return countsByLabel.entrySet().stream()
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

    private static Map<String, Long> countLabelsByTurnaround(List<HistoricalCorrectiveAction> correctiveActions) {
        Map<String, Long> countsByTurnaround = new LinkedHashMap<>();
        TURNAROUND_BUCKETS.forEach(label -> countsByTurnaround.put(label, 0L));
        correctiveActions.stream()
            .filter(HistoricalCorrectiveAction::resolved)
            .map(LocationDashboardDerivedGraphSupport::turnaroundLabel)
            .filter(Objects::nonNull)
            .forEach(label -> countsByTurnaround.merge(label, 1L, Long::sum));

        return countsByTurnaround.entrySet().stream()
            .filter(entry -> entry.getValue() > 0L)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private static String turnaroundLabel(HistoricalCorrectiveAction correctiveAction) {
        LocalDate observedDate = correctiveAction.observedDate();
        ServiceEvent serviceEvent = correctiveAction.serviceEvent();
        if (observedDate == null || serviceEvent == null || serviceEvent.getEndEventDate() == null) {
            return null;
        }
        LocalDateTime observedAt = LocalDateTime.of(observedDate, LocalTime.MIDNIGHT);
        LocalDateTime resolvedAt = LocalDateTime.of(
            serviceEvent.getEndEventDate(),
            serviceEvent.getEndEventTime() == null ? LocalTime.MIDNIGHT : serviceEvent.getEndEventTime()
        );
        long turnaroundDays = Math.max(0L, ChronoUnit.DAYS.between(observedAt, resolvedAt));
        if (turnaroundDays < 3L) {
            return "< 3 days";
        }
        if (turnaroundDays < 7L) {
            return "< 1 week";
        }
        if (turnaroundDays < 31L) {
            return "< 1 month";
        }
        if (turnaroundDays < 92L) {
            return "< 3 months";
        }
        return null;
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
        Map<String, Object> marker = copyMap(trace.get("marker"));
        marker.putIfAbsent("color", colors.isEmpty() ? DEFAULT_GRAPH_COLOR : colors.getFirst());
        marker.putIfAbsent("colors", List.copyOf(colors));
        trace.put("marker", marker);
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
        bar.putIfAbsent("color", color);
        gauge.put("bar", bar);
        trace.put("gauge", gauge);
        return trace;
    }

    private static Map<String, Object> buildCountIndicatorTrace(
        Graph graph,
        String traceName,
        long value,
        String color
    ) {
        Map<String, Object> trace = indicatorPrototype(graph, traceName);
        trace.put("mode", "number");
        trace.put("value", value);
        Map<String, Object> number = copyMap(trace.get("number"));
        number.remove("suffix");
        number.putIfAbsent("font", Map.of("size", 22));
        trace.put("number", number);
        trace.remove("gauge");
        Map<String, Object> domain = copyMap(trace.get("domain"));
        domain.putIfAbsent("x", List.of(0, 1));
        domain.putIfAbsent("y", List.of(0, 1));
        trace.put("domain", domain);
        Map<String, Object> title = copyMap(trace.get("title"));
        title.putIfAbsent("font", Map.of("size", 14, "color", color));
        trace.put("title", title);
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
        List<Long> values = labels.stream().map(countsByLabel::get).toList();
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
        // Preserve the configured bar direction so uploads only refresh values, not chart layout.
        String orientation = resolveBarOrientation(trace);
        trace.put("orientation", orientation);
        if ("v".equals(orientation)) {
            trace.put("x", List.copyOf(labels));
            trace.put("y", List.copyOf(values));
        } else {
            trace.put("x", List.copyOf(values));
            trace.put("y", List.copyOf(labels));
        }
        Map<String, Object> marker = copyMap(trace.get("marker"));
        String preservedColor = configuredTraceColor(trace);
        marker.put("color", preservedColor == null ? color : preservedColor);
        trace.put("marker", marker);
        return trace;
    }

    private static String configuredTraceColor(Map<String, Object> trace) {
        Map<String, Object> marker = copyMap(trace.get("marker"));
        Object markerColor = marker.get("color");
        if (markerColor instanceof String colorValue && !colorValue.isBlank()) {
            return colorValue;
        }
        Object markerColors = marker.get("colors");
        if (markerColors instanceof List<?> colors) {
            for (Object colorValue : colors) {
                if (colorValue instanceof String color && !color.isBlank()) {
                    return color;
                }
            }
        }
        Map<String, Object> line = copyMap(trace.get("line"));
        Object lineColor = line.get("color");
        if (lineColor instanceof String colorValue && !colorValue.isBlank()) {
            return colorValue;
        }
        return null;
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
        trace.putIfAbsent("orientation", "h");
        return trace;
    }

    private static String resolveBarOrientation(Map<String, Object> trace) {
        Object rawOrientation = trace.get("orientation");
        if (rawOrientation instanceof String orientation) {
            String normalized = orientation.strip().toLowerCase(Locale.ROOT);
            if ("h".equals(normalized) || "v".equals(normalized)) {
                return normalized;
            }
        }
        return "h";
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

    private static String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static String labelOrFallback(String value, String fallbackLabel) {
        String normalizedLabel = normalizeLabel(value);
        return normalizedLabel == null ? fallbackLabel : normalizedLabel;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    record HistoricalSamplePoint(
        LocalDate observedDate,
        String facilityName,
        String measurementName,
        String systemTypeName,
        long sampleCount,
        long compliantCount
    ) {
        long nonConformingCount() {
            return Math.max(0L, sampleCount - compliantCount);
        }
    }

    record HistoricalCorrectiveAction(
        LocalDate observedDate,
        String facilityName,
        String systemTypeName,
        String measurementName,
        ServiceEvent serviceEvent
    ) {
        boolean resolved() {
            return serviceEvent != null && serviceEvent.getStatus() == ServiceEventStatus.COMPLETED;
        }
    }

    record HistoricalDerivedData(
        Map<LocalDate, List<HistoricalSamplePoint>> samplesByDate,
        List<HistoricalCorrectiveAction> correctiveActions
    ) {
        HistoricalDerivedData {
            Map<LocalDate, List<HistoricalSamplePoint>> normalizedSamplesByDate = new LinkedHashMap<>();
            if (samplesByDate != null) {
                samplesByDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalizedSamplesByDate.put(
                        entry.getKey(),
                        entry.getValue() == null ? List.of() : List.copyOf(entry.getValue())
                    ));
            }
            samplesByDate = Map.copyOf(normalizedSamplesByDate);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
        }

        List<HistoricalSamplePoint> allSamplePoints() {
            Set<HistoricalSamplePoint> samplePoints = new LinkedHashSet<>();
            for (List<HistoricalSamplePoint> bucket : samplesByDate.values()) {
                samplePoints.addAll(bucket);
            }
            return List.copyOf(samplePoints);
        }
    }
}
