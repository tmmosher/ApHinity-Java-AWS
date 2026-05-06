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
    List<Map<String, Object>> mergeImportedGraphData(Graph graph, List<Map<String, Object>> importedData) {
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
            return mergeTraceWithExistingSeries(existingTrace, importedTrace);
        }
        if (!existingTimeSeries || !importedTimeSeries) {
            return importedTrace;
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
        mergedTrace.putAll(existingTrace);
        mergedTrace.putAll(importedTrace);
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

    private Map<String, Object> mergeTraceWithExistingSeries(Map<String, Object> existingTrace, Map<String, Object> importedTrace) {
        Map<String, Object> mergedTrace = new LinkedHashMap<>();
        mergedTrace.putAll(existingTrace);
        mergedTrace.putAll(importedTrace);
        mergedTrace.put("x", existingTrace.get("x"));
        mergedTrace.put("y", existingTrace.get("y"));
        if (existingTrace.containsKey("customdata")) {
            mergedTrace.put("customdata", existingTrace.get("customdata"));
        } else {
            mergedTrace.remove("customdata");
        }
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
