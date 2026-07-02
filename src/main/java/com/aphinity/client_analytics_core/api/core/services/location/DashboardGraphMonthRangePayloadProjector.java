package com.aphinity.client_analytics_core.api.core.services.location;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projects Plotly payloads to a requested dashboard month window without
 * mutating the stored all-time graph data.
 */
public final class DashboardGraphMonthRangePayloadProjector {
    private static final DateTimeFormatter FLEXIBLE_LOCAL_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
        .appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    private DashboardGraphMonthRangePayloadProjector() {
    }

    /**
     * Filters time-series scatter traces to the requested month range while
     * leaving non-time-series traces unchanged.
     *
     * @param allTimePayload graph data persisted for all time
     * @param monthRange requested month range
     * @param anchorDate upper-bound reference date for the rolling window
     * @return projected payload suitable for API responses
     */
    public static List<Map<String, Object>> project(
        List<Map<String, Object>> allTimePayload,
        DashboardGraphMonthRange monthRange,
        LocalDate anchorDate
    ) {
        if (allTimePayload == null || allTimePayload.isEmpty() || monthRange == null || monthRange.isAllTime()) {
            return allTimePayload == null ? List.of() : allTimePayload;
        }
        LocalDate windowStart = monthRange.windowStartInclusive(anchorDate);
        if (windowStart == null) {
            return allTimePayload;
        }

        List<Map<String, Object>> projectedPayload = new ArrayList<>(allTimePayload.size());
        for (Map<String, Object> trace : allTimePayload) {
            projectedPayload.add(projectTrace(trace, windowStart));
        }
        return List.copyOf(projectedPayload);
    }

    /**
     * Identifies scatter traces whose x-axis values can all be parsed as dates.
     *
     * @param trace Plotly trace payload
     * @return true when the trace can be safely range-projected
     */
    public static boolean isTimeSeriesTrace(Map<String, Object> trace) {
        if (trace == null) {
            return false;
        }
        Object rawType = trace.get("type");
        if (!(rawType instanceof String traceType)) {
            return false;
        }
        String normalizedType = traceType.strip().toLowerCase(Locale.ROOT);
        if (!"scatter".equals(normalizedType) && !"scattergl".equals(normalizedType)) {
            return false;
        }
        List<?> xValues = asList(trace.get("x"));
        return !xValues.isEmpty() && xValues.stream().allMatch(value -> parseLocalDate(value) != null);
    }

    private static Map<String, Object> projectTrace(Map<String, Object> trace, LocalDate windowStart) {
        if (!isTimeSeriesTrace(trace)) {
            return trace;
        }

        List<?> xValues = asList(trace.get("x"));
        List<?> yValues = asList(trace.get("y"));
        List<?> customDataValues = asList(trace.get("customdata"));
        int pointCount = Math.min(xValues.size(), yValues.size());

        List<Object> filteredXValues = new ArrayList<>();
        List<Object> filteredYValues = new ArrayList<>();
        List<Object> filteredCustomDataValues = new ArrayList<>();
        boolean hasCustomData = false;

        for (int index = 0; index < pointCount; index += 1) {
            LocalDate observedDate = parseLocalDate(xValues.get(index));
            if (observedDate == null || observedDate.isBefore(windowStart)) {
                continue;
            }
            filteredXValues.add(xValues.get(index));
            filteredYValues.add(yValues.get(index));
            Object customDataValue = index < customDataValues.size() ? customDataValues.get(index) : null;
            filteredCustomDataValues.add(customDataValue);
            if (customDataValue != null) {
                hasCustomData = true;
            }
        }

        Map<String, Object> filteredTrace = new LinkedHashMap<>(trace);
        filteredTrace.put("x", List.copyOf(filteredXValues));
        filteredTrace.put("y", List.copyOf(filteredYValues));
        if (hasCustomData) {
            filteredTrace.put("customdata", List.copyOf(filteredCustomDataValues));
        } else {
            filteredTrace.remove("customdata");
        }
        return filteredTrace;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> listValue ? listValue : List.of();
    }

    private static LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        String rawValue = String.valueOf(value).strip();
        if (rawValue.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(rawValue).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(rawValue).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(rawValue, FLEXIBLE_LOCAL_DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
