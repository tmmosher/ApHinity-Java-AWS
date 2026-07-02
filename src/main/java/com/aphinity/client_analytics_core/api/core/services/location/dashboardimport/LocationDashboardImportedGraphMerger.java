package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Upserts imported graph points into the persisted graph payload.
 * The key invariant is that sparse uploads must not erase historical dates for
 * configured traces that happen to have no points in the current workbook.
 */
final class LocationDashboardImportedGraphMerger {
    private static final Set<String> TRACE_DATA_KEYS = Set.of(
        "x",
        "y",
        "labels",
        "values",
        "value",
        "customdata"
    );
    private static final Set<String> TRACE_IDENTITY_KEYS = Set.of(
        "type",
        "name",
        "mode",
        "orientation"
    );

    /**
     * Merges imported data into a graph while preserving existing sparse
     * time-series history.
     *
     * @param graph persisted graph
     * @param importedData newly computed import traces
     * @return merged graph data
     */
    List<Map<String, Object>> mergeImportedGraphData(Graph graph, List<Map<String, Object>> importedData) {
        return mergeImportedGraphData(graph, importedData, false);
    }

    /**
     * Merges imported traces and optionally replaces all existing points.
     *
     * @param graph persisted graph
     * @param importedData newly computed import traces
     * @param resetExistingPoints whether existing trace points should be discarded
     * @return merged graph data
     */
    List<Map<String, Object>> mergeImportedGraphData(
        Graph graph,
        List<Map<String, Object>> importedData,
        boolean resetExistingPoints
    ) {
        List<Map<String, Object>> existingData = LocationDashboardGraphMetadataSupport.currentTraceList(graph);
        if (existingData.isEmpty()) {
            return importedData;
        }

        Map<String, Map<String, Object>> existingTracesByIdentity = new LinkedHashMap<>();
        List<String> existingTraceOrder = new ArrayList<>();
        for (int index = 0; index < existingData.size(); index += 1) {
            Map<String, Object> existingTrace = existingData.get(index);
            String traceIdentity = traceIdentity(existingTrace, index);
            existingTracesByIdentity.putIfAbsent(traceIdentity, existingTrace);
            existingTraceOrder.add(traceIdentity);
        }

        if (resetExistingPoints) {
            List<Map<String, Object>> resetTraces = new ArrayList<>();
            for (int index = 0; index < importedData.size(); index += 1) {
                Map<String, Object> importedTrace = importedData.get(index);
                resetTraces.add(mergeTracePreservingPresentation(
                    existingTracesByIdentity.get(traceIdentity(importedTrace, index)),
                    importedTrace
                ));
            }
            return List.copyOf(resetTraces);
        }

        List<Map<String, Object>> mergedTraces = new ArrayList<>();
        Set<String> mergedTraceIdentities = new LinkedHashSet<>();
        for (int index = 0; index < importedData.size(); index += 1) {
            Map<String, Object> importedTrace = importedData.get(index);
            String traceIdentity = traceIdentity(importedTrace, index);
            mergedTraces.add(mergeImportedTrace(existingTracesByIdentity.get(traceIdentity), importedTrace));
            mergedTraceIdentities.add(traceIdentity);
        }

        for (String existingTraceIdentity : existingTraceOrder) {
            Map<String, Object> existingTrace = existingTracesByIdentity.get(existingTraceIdentity);
            if (!mergedTraceIdentities.contains(existingTraceIdentity) && traceHasPersistedPoints(existingTrace)) {
                mergedTraces.add(existingTrace);
            }
        }

        return List.copyOf(mergedTraces);
    }

    private Map<String, Object> mergeImportedTrace(Map<String, Object> existingTrace, Map<String, Object> importedTrace) {
        if (existingTrace == null) {
            return importedTrace;
        }

        boolean existingTimeSeries = isTimeSeriesScatterTrace(existingTrace);
        boolean importedTimeSeries = isTimeSeriesScatterTrace(importedTrace);
        if (existingTimeSeries && importedTraceHasNoNewPoints(importedTrace)) {
            // Sparse uploads should preserve the existing trace exactly, including styling.
            return new LinkedHashMap<>(existingTrace);
        }
        if (!existingTimeSeries || !importedTimeSeries) {
            return mergeTracePreservingPresentation(existingTrace, importedTrace);
        }

        Map<String, TracePointValue> pointsByDate = new LinkedHashMap<>();
        collectTracePoints(existingTrace, pointsByDate);
        collectTracePoints(importedTrace, pointsByDate);
        List<Map.Entry<String, TracePointValue>> orderedPoints = pointsByDate.entrySet().stream()
            .sorted(Comparator
                .comparing((Map.Entry<String, TracePointValue> entry) -> LocationDashboardGraphMetadataSupport.parseLocalDate(entry.getKey()))
                .thenComparing(Map.Entry::getKey))
            .toList();

        Map<String, Object> mergedTrace = new LinkedHashMap<>();
        mergedTrace.putAll(importedTrace);
        mergedTrace.putAll(existingTrace);
        mergeNestedTracePresentationFields(mergedTrace, importedTrace, existingTrace);
        mergedTrace.put("x", orderedPoints.stream().map(Map.Entry::getKey).toList());
        mergedTrace.put("y", orderedPoints.stream().map(entry -> entry.getValue().yValue()).toList());

        List<Object> mergedCustomData = orderedPoints.stream()
            .map(entry -> entry.getValue().customData())
            .toList();
        if (mergedCustomData.stream().anyMatch(Objects::nonNull)) {
            mergedTrace.put("customdata", mergedCustomData);
        } else {
            mergedTrace.remove("customdata");
        }
        return mergedTrace;
    }

    private void mergeNestedTracePresentationFields(
        Map<String, Object> mergedTrace,
        Map<String, Object> importedTrace,
        Map<String, Object> existingTrace
    ) {
        Set<String> candidateFieldNames = new LinkedHashSet<>();
        candidateFieldNames.addAll(importedTrace.keySet());
        candidateFieldNames.addAll(existingTrace.keySet());
        for (String fieldName : candidateFieldNames) {
            if (TRACE_DATA_KEYS.contains(fieldName) || TRACE_IDENTITY_KEYS.contains(fieldName)) {
                continue;
            }
            Map<String, Object> importedField = LocationDashboardGraphMetadataSupport.asMap(importedTrace.get(fieldName));
            Map<String, Object> existingField = LocationDashboardGraphMetadataSupport.asMap(existingTrace.get(fieldName));
            if (importedField.isEmpty() && existingField.isEmpty()) {
                continue;
            }
            Map<String, Object> mergedField = new LinkedHashMap<>(importedField);
            mergedField.putAll(existingField);
            mergedTrace.put(fieldName, mergedField);
        }
    }

    private Map<String, Object> mergeTracePreservingPresentation(
        Map<String, Object> existingTrace,
        Map<String, Object> importedTrace
    ) {
        if (existingTrace == null) {
            return importedTrace;
        }
        // Persisted trace styling should win, but blank placeholder payloads must not wipe
        // out the imported workbook points for the same named trace.
        Map<String, Object> mergedTrace = new LinkedHashMap<>(importedTrace);
        existingTrace.forEach((key, value) -> {
            if (!TRACE_DATA_KEYS.contains(key)) {
                mergedTrace.put(key, value);
            }
        });
        mergeNestedTracePresentationFields(mergedTrace, importedTrace, existingTrace);
        return mergedTrace;
    }

    private void collectTracePoints(Map<String, Object> trace, Map<String, TracePointValue> pointsByDate) {
        List<?> xValues = LocationDashboardGraphMetadataSupport.asList(trace.get("x"));
        List<?> yValues = LocationDashboardGraphMetadataSupport.asList(trace.get("y"));
        List<?> customDataValues = LocationDashboardGraphMetadataSupport.asList(trace.get("customdata"));
        int pointCount = Math.min(xValues.size(), yValues.size());
        for (int index = 0; index < pointCount; index += 1) {
            Object rawX = xValues.get(index);
            if (LocationDashboardGraphMetadataSupport.parseLocalDate(rawX) == null) {
                continue;
            }
            pointsByDate.put(String.valueOf(rawX), new TracePointValue(
                yValues.get(index),
                index < customDataValues.size() ? customDataValues.get(index) : null
            ));
        }
    }

    private boolean isTimeSeriesScatterTrace(Map<String, Object> trace) {
        if (!Objects.equals(LocationDashboardGraphMetadataSupport.normalizeKey(String.valueOf(trace.get("type"))), "scatter")) {
            return false;
        }
        List<?> xValues = LocationDashboardGraphMetadataSupport.asList(trace.get("x"));
        return !xValues.isEmpty()
            && xValues.stream().allMatch(value -> LocationDashboardGraphMetadataSupport.parseLocalDate(value) != null);
    }

    private boolean importedTraceHasNoNewPoints(Map<String, Object> importedTrace) {
        return LocationDashboardGraphMetadataSupport.asList(importedTrace.get("x")).isEmpty()
            && LocationDashboardGraphMetadataSupport.asList(importedTrace.get("y")).isEmpty();
    }

    private boolean traceHasPersistedPoints(Map<String, Object> trace) {
        if (trace == null) {
            return false;
        }
        if (!LocationDashboardGraphMetadataSupport.asList(trace.get("x")).isEmpty()
            || !LocationDashboardGraphMetadataSupport.asList(trace.get("y")).isEmpty()) {
            return true;
        }
        if (!LocationDashboardGraphMetadataSupport.asList(trace.get("labels")).isEmpty()
            || !LocationDashboardGraphMetadataSupport.asList(trace.get("values")).isEmpty()) {
            return true;
        }
        return trace.get("value") != null;
    }

    private String traceIdentity(Map<String, Object> trace, int fallbackIndex) {
        String traceName = traceName(trace);
        if (traceName != null) {
            return LocationDashboardGraphMetadataSupport.normalizeKey(traceName);
        }
        return "trace-" + fallbackIndex;
    }

    private String traceName(Map<String, Object> trace) {
        Object rawName = trace.get("name");
        if (!(rawName instanceof String traceName) || traceName.isBlank()) {
            return null;
        }
        return traceName;
    }

    private record TracePointValue(
        Object yValue,
        Object customData
    ) {
    }
}
