package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CartesianTraceDateOrderCanonicalizer {
    List<Map<String, Object>> canonicalize(List<Map<String, Object>> traces) {
        if (traces == null || traces.isEmpty()) {
            return List.of();
        }

        boolean changed = false;
        List<Map<String, Object>> canonicalized = new ArrayList<>(traces.size());
        for (Map<String, Object> trace : traces) {
            Map<String, Object> canonicalTrace = canonicalizeTrace(trace);
            if (canonicalTrace != trace) {
                changed = true;
            }
            canonicalized.add(canonicalTrace);
        }

        return changed ? List.copyOf(canonicalized) : traces;
    }

    private Map<String, Object> canonicalizeTrace(Map<String, Object> trace) {
        if (trace == null || !isSortableScatterTrace(trace)) {
            return trace;
        }

        Object rawXValues = trace.get("x");
        if (!(rawXValues instanceof List<?> xValues)) {
            return trace;
        }
        if (xValues.size() < 2) {
            return trace;
        }

        boolean hasDateValue = false;
        List<TracePointOrder> orderedPoints = new ArrayList<>(xValues.size());
        for (int index = 0; index < xValues.size(); index += 1) {
            Object rawX = xValues.get(index);
            LocalDate observedDate = parseIsoDate(rawX);
            if (observedDate != null) {
                hasDateValue = true;
                orderedPoints.add(new TracePointOrder(index, observedDate, true));
                continue;
            }
            if (rawX == null || (rawX instanceof String stringValue && stringValue.isBlank())) {
                orderedPoints.add(new TracePointOrder(index, null, false));
                continue;
            }
            return trace;
        }

        if (!hasDateValue) {
            return trace;
        }

        List<TracePointOrder> sortedPoints = orderedPoints.stream()
            .sorted(Comparator
                .comparing(TracePointOrder::sortable).reversed()
                .thenComparing(
                    TracePointOrder::observedDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparingInt(TracePointOrder::index))
            .toList();

        boolean alreadySorted = true;
        for (int index = 0; index < sortedPoints.size(); index += 1) {
            if (sortedPoints.get(index).index() != index) {
                alreadySorted = false;
                break;
            }
        }
        if (alreadySorted) {
            return trace;
        }

        Map<String, Object> canonicalTrace = new LinkedHashMap<>(trace);
        for (Map.Entry<String, Object> entry : trace.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List<?> listValue) || listValue.size() != xValues.size()) {
                continue;
            }
            List<Object> reorderedValues = new ArrayList<>(listValue.size());
            for (TracePointOrder point : sortedPoints) {
                reorderedValues.add(listValue.get(point.index()));
            }
            canonicalTrace.put(entry.getKey(), List.copyOf(reorderedValues));
        }
        return canonicalTrace;
    }

    private boolean isSortableScatterTrace(Map<String, Object> trace) {
        Object rawType = trace.get("type");
        if (!(rawType instanceof String traceType)) {
            return false;
        }
        String normalizedType = traceType.strip().toLowerCase(Locale.ROOT);
        return "scatter".equals(normalizedType) || "scattergl".equals(normalizedType);
    }

    private LocalDate parseIsoDate(Object rawValue) {
        if (!(rawValue instanceof String stringValue)) {
            return null;
        }
        String normalized = stringValue.strip();
        try {
            LocalDate parsed = LocalDate.parse(normalized);
            return parsed.toString().equals(normalized) ? parsed : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record TracePointOrder(
        int index,
        LocalDate observedDate,
        boolean sortable
    ) {
    }
}
