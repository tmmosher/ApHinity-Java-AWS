package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private static final List<String> TURNAROUND_BUCKETS = List.of("< 1 week", "< 2 weeks", "< 1 month", "< 3 months");
    private static final ZoneId DASHBOARD_TIME_ZONE = ZoneId.of("America/Phoenix");

    private LocationDashboardDerivedGraphSupport() {
    }

    static String metadataValue(LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType) {
        return derivedGraphType == null ? null : derivedGraphType.value();
    }

    static List<Map<String, Object>> buildPayload(
        LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedGraphDefinition,
        Graph graph,
        HistoricalDerivedData historicalData,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        return buildPayload(
            derivedGraphDefinition,
            graph,
            historicalData,
            identityPattern,
            LocalDate.now(DASHBOARD_TIME_ZONE)
        );
    }

    static List<Map<String, Object>> buildPayload(
        LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedGraphDefinition,
        Graph graph,
        HistoricalDerivedData historicalData,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern,
        LocalDate anchorDate
    ) {
        LocationDashboardImportStrategyConfig.DerivedGraphType derivedGraphType = derivedGraphDefinition.derivedType();
        List<HistoricalSamplePoint> waterQualitySamplePoints = historicalData.allSamplePoints().stream()
            .filter(samplePoint -> samplePoint.measurementName() != null)
            .toList();
        List<HistoricalSamplePoint> systemTypeSamplePoints = historicalData.allSamplePoints().stream()
            .filter(samplePoint -> samplePoint.systemTypeName() != null)
            .toList();
        List<HistoricalNonConformance> nonConformances = historicalData.nonConformances();

        long totalSamples = waterQualitySamplePoints.stream().mapToLong(HistoricalSamplePoint::sampleCount).sum();
        long compliantSamples = waterQualitySamplePoints.stream().mapToLong(HistoricalSamplePoint::compliantCount).sum();
        long totalSampleNonConformances = waterQualitySamplePoints.stream()
            .mapToLong(HistoricalSamplePoint::nonConformingCount)
            .sum();
        long totalIncidentNonConformances = nonConformances.size();
        long resolvedNonConformances = nonConformances.stream().filter(HistoricalNonConformance::resolved).count();
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
            case NON_CONFORMANCE_STATUS_BY_FACILITY -> buildStatusByFacilityPayload(graph, nonConformances);
            case NON_CONFORMANCE_TURNAROUND_TIME -> List.of(buildHorizontalBarTrace(
                graph,
                "Resolved",
                countLabelsByTurnaround(nonConformances),
                RESOLVED_GRAPH_COLOR,
                0
            ));
            case RECENT_SAMPLE_MEASUREMENTS -> List.of(buildRecentSampleMeasurementsTrace(
                historicalData.recentRawSamples(),
                identityPattern,
                anchorDate
            ));
            case SAMPLE_CONFORMANCE_HIERARCHY -> List.of(SampleConformanceSunburstBuilder.build(
                findExistingTrace(graph, "sunburst", 0),
                derivedGraphDefinition.name(),
                historicalData.rawSamples(),
                derivedGraphDefinition.hierarchy()
            ));
        };
    }

    private static Map<String, Object> buildRecentSampleMeasurementsTrace(
        List<HistoricalRawSample> rawSamples,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern,
        LocalDate anchorDate
    ) {
        RecentSampleMeasurementsTable table = buildRecentSampleMeasurementsTable(
            rawSamples,
            identityPattern,
            anchorDate
        );

        List<List<Object>> columns = table.headers().stream()
            .map(ignored -> new ArrayList<>())
            .collect(Collectors.toCollection(ArrayList::new));
        List<Map<String, Object>> customData = new ArrayList<>();
        for (Map<String, Object> row : table.rows()) {
            for (int index = 0; index < table.fields().size(); index += 1) {
                columns.get(index).add(row.getOrDefault(table.fields().get(index), ""));
            }
            customData.add(Map.of(
                "rowIdentifier", row.getOrDefault("rowIdentifier", ""),
                "caStatus", row.getOrDefault("caStatus", ""),
                "followUps", row.getOrDefault("followUps", List.of())
            ));
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("type", "table");
        trace.put("name", "Recent Sample Measurements");
        trace.put("header", Map.of(
            "values", table.headers(),
            "align", "left"
        ));
        trace.put("cells", Map.of(
            "values", columns.stream().map(List::copyOf).toList(),
            "align", "left"
        ));
        trace.put("customdata", List.copyOf(customData));
        trace.put("meta", Map.of(
            "renderer", "tabulator",
            "rowKey", "rowIdentifier"
        ));
        return trace;
    }

    static RecentSampleMeasurementsTable buildRecentSampleMeasurementsTable(
        List<HistoricalRawSample> rawSamples,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern,
        LocalDate anchorDate
    ) {
        List<IdentityColumnDefinition> identityColumns = identityColumns(identityPattern);
        LocalDate windowStart = recentSampleWindowStart(anchorDate);
        Map<String, RecentSampleRow> rowsByIdentifier = new LinkedHashMap<>();
        for (HistoricalRawSample sample : rawSamples == null ? List.<HistoricalRawSample>of() : rawSamples) {
            if (sample == null || sample.observedDate() == null || sample.measurementName() == null) {
                continue;
            }
            if (windowStart != null && sample.observedDate().isBefore(windowStart)) {
                continue;
            }
            String rowIdentifier = sample.rowIdentifier();
            if (rowIdentifier == null) {
                continue;
            }
            RecentSampleRow current = rowsByIdentifier.get(rowIdentifier);
            if (current == null) {
                rowsByIdentifier.put(rowIdentifier, RecentSampleRow.from(sample));
                continue;
            }
            if (sameMonth(sample.observedDate(), current.observedDate())) {
                if (sample.observedDate().isAfter(current.observedDate())) {
                    current.addMonthlyMeasurement(sample);
                    continue;
                }
                if (sample.observedDate().isEqual(current.observedDate())) {
                    if (current.compliant && !sample.compliant()) {
                        RecentSampleRow next = RecentSampleRow.from(sample);
                        next.addMonthlyMeasurement(current.toHistoricalRawSample());
                        current.followUps().forEach(next::addFollowUp);
                        next.inheritPresentationResolution(current);
                        rowsByIdentifier.put(rowIdentifier, next);
                        continue;
                    }
                    current.addMonthlyMeasurement(sample);
                    continue;
                }
                RecentSampleRow next = RecentSampleRow.from(sample);
                next.addMonthlyMeasurement(current.toHistoricalRawSample());
                current.followUps().forEach(next::addFollowUp);
                next.inheritPresentationResolution(current);
                rowsByIdentifier.put(rowIdentifier, next);
            }
        }

        List<RecentSampleRow> rows = rowsByIdentifier.values().stream()
            .sorted(Comparator
                .comparing(RecentSampleRow::observedDate, Comparator.reverseOrder())
                .thenComparing(RecentSampleRow::rowIdentifier))
            .toList();
        List<String> headers = new ArrayList<>();
        identityColumns.stream().map(IdentityColumnDefinition::label).forEach(headers::add);
        headers.add("Measurement");
        headers.add("Observed");
        headers.add("Value");
        headers.add("Follow-ups");

        List<String> fields = headers.stream()
            .map(LocationDashboardDerivedGraphSupport::slugField)
            .toList();
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (RecentSampleRow row : rows) {
            Map<String, Object> tableRow = new LinkedHashMap<>();
            int fieldIndex = 0;
            for (IdentityColumnDefinition identityColumn : identityColumns) {
                tableRow.put(fields.get(fieldIndex), row.identityValues().getOrDefault(identityColumn.key(), ""));
                fieldIndex += 1;
            }
            tableRow.put(fields.get(fieldIndex++), row.measurementName());
            tableRow.put(fields.get(fieldIndex++), String.valueOf(row.observedDate()));
            tableRow.put(fields.get(fieldIndex++), row.rawValue());
            tableRow.put(fields.get(fieldIndex), row.followUps().isEmpty() ? "" : row.followUps().size());
            tableRow.put("rowIdentifier", row.rowIdentifier());
            tableRow.put("caStatus", row.caStatus());
            tableRow.put("followUps", row.followUps());
            tableRows.add(Map.copyOf(tableRow));
        }
        return new RecentSampleMeasurementsTable(headers, fields, tableRows);
    }

    private static String slugField(String header) {
        String normalized = header == null ? "" : header.strip().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "column" : normalized;
    }

    private static LocalDate recentSampleWindowStart(LocalDate anchorDate) {
        LocalDate effectiveAnchorDate = anchorDate == null ? LocalDate.now(DASHBOARD_TIME_ZONE) : anchorDate;
        return effectiveAnchorDate.minusMonths(1).withDayOfMonth(1);
    }

    private static List<IdentityColumnDefinition> identityColumns(
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> effectivePattern =
            identityPattern == null || identityPattern.isEmpty()
                ? List.of()
                : identityPattern;
        return effectivePattern.stream()
            .filter(Objects::nonNull)
            .map(column -> new IdentityColumnDefinition(column.identityKey(), identityColumnLabel(column.column())))
            .toList();
    }

    private static String identityColumnLabel(String key) {
        if (key == null || key.isBlank()) {
            return "Identity";
        }
        String[] tokens = key.strip().replace('_', ' ').split("(?=[A-Z])|\\s+");
        List<String> labels = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalized = token.strip().toLowerCase(Locale.ROOT);
            labels.add(normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1));
        }
        return labels.isEmpty() ? key : String.join(" ", labels);
    }

    private static boolean sameMonth(LocalDate left, LocalDate right) {
        return left != null
            && right != null
            && left.getYear() == right.getYear()
            && left.getMonth() == right.getMonth();
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
        List<HistoricalNonConformance> nonConformances
    ) {
        Map<String, long[]> countsByFacility = new LinkedHashMap<>();
        for (HistoricalNonConformance nonConformance : nonConformances) {
            String facilityName = labelOrFallback(nonConformance.facilityName(), UNKNOWN_FACILITY_LABEL);
            long[] counts = countsByFacility.computeIfAbsent(facilityName, ignored -> new long[2]);
            if (nonConformance.resolved()) {
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

    private static Map<String, Long> countLabelsByTurnaround(List<HistoricalNonConformance> nonConformances) {
        Map<String, Long> countsByTurnaround = new LinkedHashMap<>();
        TURNAROUND_BUCKETS.forEach(label -> countsByTurnaround.put(label, 0L));
        nonConformances.stream()
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

    private static String turnaroundLabel(HistoricalNonConformance nonConformance) {
        if (nonConformance == null || nonConformance.turnaroundDays() == null) {
            return null;
        }
        long turnaroundDays = Math.max(0L, nonConformance.turnaroundDays());
        if (turnaroundDays < 7L) {
            return "< 1 week";
        }
        if (turnaroundDays < 14L) {
            return "< 2 weeks";
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
        Map<String, Object> marker = copyMap(trace.get("marker"));
        String preservedColor = configuredTraceColor(trace);
        List<String> colors = configuredBarColorsByLabel(trace, labels, orientation, preservedColor == null ? color : preservedColor);
        if ("v".equals(orientation)) {
            trace.put("x", List.copyOf(labels));
            trace.put("y", List.copyOf(values));
        } else {
            trace.put("x", List.copyOf(values));
            trace.put("y", List.copyOf(labels));
        }
        marker.put("color", colors);
        marker.put("colors", colors);
        trace.put("marker", marker);
        return trace;
    }

    private static List<String> configuredBarColorsByLabel(
        Map<String, Object> trace,
        List<String> nextLabels,
        String orientation,
        String fallbackColor
    ) {
        List<?> existingLabels = listValue(trace.get("v".equals(orientation) ? "x" : "y"));
        List<?> existingColors = markerColors(trace);
        Map<String, String> colorsByLabel = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(existingLabels.size(), existingColors.size()); index += 1) {
            Object rawColor = existingColors.get(index);
            if (rawColor instanceof String color && !color.isBlank()) {
                colorsByLabel.put(String.valueOf(existingLabels.get(index)), color);
            }
        }
        return nextLabels.stream()
            .map(label -> colorsByLabel.getOrDefault(label, fallbackColor))
            .toList();
    }

    private static String configuredTraceColor(Map<String, Object> trace) {
        Map<String, Object> marker = copyMap(trace.get("marker"));
        Object markerColor = marker.get("color");
        if (markerColor instanceof String colorValue && !colorValue.isBlank()) {
            return colorValue;
        }
        for (Object colorValue : markerColors(trace)) {
            if (colorValue instanceof String color && !color.isBlank()) {
                return color;
            }
        }
        Map<String, Object> line = copyMap(trace.get("line"));
        Object lineColor = line.get("color");
        if (lineColor instanceof String colorValue && !colorValue.isBlank()) {
            return colorValue;
        }
        return null;
    }

    private static List<?> markerColors(Map<String, Object> trace) {
        Map<String, Object> marker = copyMap(trace.get("marker"));
        Object markerColor = marker.get("color");
        if (markerColor instanceof List<?> colors) {
            return colors;
        }
        Object markerColors = marker.get("colors");
        return markerColors instanceof List<?> colors ? colors : List.of();
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
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
            return GraphPayloadMapper.toTraceList(GraphRelationalPayloadMapper.readData(graph));
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

    record RecentSampleMeasurementsTable(
        List<String> headers,
        List<String> fields,
        List<Map<String, Object>> rows
    ) {
        RecentSampleMeasurementsTable {
            headers = headers == null ? List.of() : List.copyOf(headers);
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    record HistoricalRawSample(
        LocalDate observedDate,
        String rowIdentifier,
        Map<String, String> identityValues,
        String measurementName,
        String rawValue,
        String units,
        boolean compliant,
        boolean resolved
    ) {
        HistoricalRawSample {
            identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
        }
    }

    record HistoricalCorrectiveAction(
        LocalDate observedDate,
        String facilityName,
        String measurementName,
        Map<String, String> identityValues,
        String sampleIdentity,
        ServiceEvent serviceEvent
    ) {
        HistoricalCorrectiveAction {
            identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
        }

        boolean resolved() {
            return serviceEvent != null && serviceEvent.getStatus() == ServiceEventStatus.COMPLETED;
        }

        Long turnaroundDays() {
            if (!resolved() || observedDate == null || serviceEvent == null || serviceEvent.getEndEventDate() == null) {
                return null;
            }
            LocalDateTime observedAt = LocalDateTime.of(observedDate, LocalTime.MIDNIGHT);
            LocalDateTime resolvedAt = LocalDateTime.of(
                serviceEvent.getEndEventDate(),
                serviceEvent.getEndEventTime() == null ? LocalTime.MIDNIGHT : serviceEvent.getEndEventTime()
            );
            return Math.max(0L, ChronoUnit.DAYS.between(observedAt, resolvedAt));
        }

        String identityKey() {
            return LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                measurementName,
                observedDate,
                identityValues,
                sampleIdentity
            );
        }

        HistoricalNonConformance toHistoricalNonConformance() {
            return new HistoricalNonConformance(
                observedDate,
                facilityName,
                measurementName,
                identityValues,
                sampleIdentity,
                resolved(),
                turnaroundDays()
            );
        }
    }

    record HistoricalNonConformance(
        LocalDate observedDate,
        String facilityName,
        String measurementName,
        Map<String, String> identityValues,
        String sampleIdentity,
        boolean resolved,
        Long turnaroundDays
    ) {
        HistoricalNonConformance {
            identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
        }

        static HistoricalNonConformance merge(
            HistoricalNonConformance persisted,
            HistoricalNonConformance analyzed
        ) {
            if (persisted == null) {
                return analyzed;
            }
            if (analyzed == null) {
                return persisted;
            }
            boolean resolved = persisted.resolved || analyzed.resolved;
            Long turnaroundDays = analyzed.turnaroundDays != null
                ? analyzed.turnaroundDays
                : persisted.turnaroundDays;
            return new HistoricalNonConformance(
                analyzed.observedDate != null ? analyzed.observedDate : persisted.observedDate,
                analyzed.facilityName != null ? analyzed.facilityName : persisted.facilityName,
                analyzed.measurementName != null ? analyzed.measurementName : persisted.measurementName,
                !analyzed.identityValues.isEmpty() ? analyzed.identityValues : persisted.identityValues,
                analyzed.sampleIdentity != null ? analyzed.sampleIdentity : persisted.sampleIdentity,
                resolved,
                turnaroundDays
            );
        }
    }

    record HistoricalDerivedData(
        Map<LocalDate, List<HistoricalSamplePoint>> samplesByDate,
        List<HistoricalNonConformance> nonConformances,
        List<HistoricalRawSample> rawSamples
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
            nonConformances = nonConformances == null ? List.of() : List.copyOf(nonConformances);
            rawSamples = rawSamples == null ? List.of() : List.copyOf(rawSamples);
        }

        HistoricalDerivedData(
            Map<LocalDate, List<HistoricalSamplePoint>> samplesByDate,
            List<HistoricalNonConformance> nonConformances
        ) {
            this(samplesByDate, nonConformances, List.of());
        }

        List<HistoricalSamplePoint> allSamplePoints() {
            Set<HistoricalSamplePoint> samplePoints = new LinkedHashSet<>();
            for (List<HistoricalSamplePoint> bucket : samplesByDate.values()) {
                samplePoints.addAll(bucket);
            }
            return List.copyOf(samplePoints);
        }

        List<HistoricalRawSample> recentRawSamples() {
            return rawSamples.stream()
                .filter(sample -> sample != null && sample.observedDate() != null)
                .sorted(Comparator
                    .comparing(HistoricalRawSample::observedDate, Comparator.reverseOrder())
                    .thenComparing(HistoricalRawSample::rowIdentifier, Comparator.nullsLast(String::compareTo)))
                .toList();
        }
    }

    private record IdentityColumnDefinition(
        String key,
        String label
    ) {
    }

    private static final class RecentSampleRow {
        private final String rowIdentifier;
        private final Map<String, String> identityValues;
        private final LocalDate observedDate;
        private final String measurementName;
        private final String sourceRawValue;
        private final String rawValue;
        private final String units;
        private final boolean compliant;
        private final boolean resolved;
        private final RecentSampleResolutionChain resolutionChain = new RecentSampleResolutionChain();
        private final List<Map<String, Object>> followUps = new ArrayList<>();

        private RecentSampleRow(
            String rowIdentifier,
            Map<String, String> identityValues,
            LocalDate observedDate,
            String measurementName,
            String sourceRawValue,
            String rawValue,
            String units,
            boolean compliant,
            boolean resolved
        ) {
            this.rowIdentifier = rowIdentifier;
            this.identityValues = identityValues == null ? Map.of() : Map.copyOf(identityValues);
            this.observedDate = observedDate;
            this.measurementName = measurementName;
            this.sourceRawValue = sourceRawValue;
            this.rawValue = rawValue;
            this.units = units;
            this.compliant = compliant;
            this.resolved = resolved;
        }

        static RecentSampleRow from(HistoricalRawSample sample) {
            return new RecentSampleRow(
                sample.rowIdentifier(),
                sample.identityValues(),
                sample.observedDate(),
                sample.measurementName(),
                sample.rawValue(),
                displayValue(sample.rawValue(), sample.units()),
                sample.units(),
                sample.compliant(),
                sample.resolved()
            );
        }

        void addMonthlyMeasurement(HistoricalRawSample sample) {
            if (sample == null) {
                return;
            }
            // Table presentation tracks physical resolution of the follow-up chain.
            // Turnaround-time resolution remains encoded on the sample itself and may be true for a still-failing follow-up.
            resolutionChain.recordFollowUp(observedDate, sample);
            addFollowUp(Map.of(
                "date", String.valueOf(sample.observedDate()),
                "value", displayValue(sample.rawValue(), sample.units())
            ));
        }

        void addFollowUp(Map<String, Object> followUp) {
            if (followUp != null) {
                followUps.add(followUp);
            }
        }

        void inheritPresentationResolution(RecentSampleRow previousRoot) {
            if (previousRoot != null) {
                resolutionChain.inherit(previousRoot.resolutionChain);
            }
        }

        String rowIdentifier() {
            return rowIdentifier;
        }

        Map<String, String> identityValues() {
            return identityValues;
        }

        LocalDate observedDate() {
            return observedDate;
        }

        String measurementName() {
            return measurementName;
        }

        String rawValue() {
            return rawValue;
        }

        String units() {
            return units == null ? "" : units;
        }

        String caStatus() {
            if (compliant) {
                return "No CA Required";
            }
            return resolutionChain.resolved() ? "Resolved" : "Active";
        }

        List<Map<String, Object>> followUps() {
            return List.copyOf(followUps);
        }

        Map<String, Object> customData() {
            return Map.of(
                "rowIdentifier", rowIdentifier,
                "caStatus", caStatus(),
                "followUps", followUps()
            );
        }

        HistoricalRawSample toHistoricalRawSample() {
            return new HistoricalRawSample(
                observedDate,
                rowIdentifier,
                identityValues,
                measurementName,
                sourceRawValue,
                units,
                compliant,
                resolved
            );
        }

        private static String displayValue(String rawValue, String units) {
            String value = rawValue == null || rawValue.isBlank() ? "" : rawValue.strip();
            String normalizedUnits = units == null || units.isBlank() ? "" : units.strip();
            if (value.isBlank()) {
                return normalizedUnits;
            }
            return normalizedUnits.isBlank() ? value : value + " " + normalizedUnits;
        }
    }

    private static final class RecentSampleResolutionChain {
        private boolean hasCompliantFollowUp;

        void recordFollowUp(LocalDate rootObservedDate, HistoricalRawSample sample) {
            if (sample != null
                && sample.compliant()
                && sample.observedDate() != null
                && rootObservedDate != null
                && sample.observedDate().isAfter(rootObservedDate)) {
                hasCompliantFollowUp = true;
            }
        }

        void inherit(RecentSampleResolutionChain previousRootChain) {
            if (previousRootChain != null && previousRootChain.hasCompliantFollowUp) {
                hasCompliantFollowUp = true;
            }
        }

        boolean resolved() {
            return hasCompliantFollowUp;
        }
    }
}
