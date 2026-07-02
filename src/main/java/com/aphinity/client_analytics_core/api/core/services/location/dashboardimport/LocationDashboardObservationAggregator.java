package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType;

/**
 * Aggregates analyzed workbook samples into configured dashboard graph payloads
 * and flat observation records.
 */
final class LocationDashboardObservationAggregator {
    private static final String TIME_SERIES_LINE_SHAPE = "hv";
    private static final double TIME_SERIES_LINE_SMOOTHING = 0.3d;
    private static final List<String> DEFAULT_TRACE_COLORS = List.of(
        "#1f77b4",
        "#ff7f0e",
        "#2ca02c",
        "#d62728",
        "#9467bd",
        "#8c564b",
        "#e377c2",
        "#7f7f7f",
        "#bcbd22",
        "#17becf"
    );

    private final Map<String, List<GraphConfig>> graphsBySublocationKey;
    private final List<GraphConfig> graphDefinitions;

    LocationDashboardObservationAggregator(
        Map<String, List<GraphConfig>> graphsBySublocationKey,
        List<GraphConfig> graphDefinitions
    ) {
        this.graphsBySublocationKey = graphsBySublocationKey == null ? Map.of() : graphsBySublocationKey;
        this.graphDefinitions = graphDefinitions == null ? List.of() : List.copyOf(graphDefinitions);
    }

    /**
     * Builds graph payloads and observation records from analyzed imported samples.
     *
     * @param analyzedSamples samples after compliance and resolution analysis
     * @return aggregation output consumed by the import service
     */
    ObservationAggregationResult aggregate(List<LocationDashboardAnalyzedSample> analyzedSamples) {
        Map<String, GraphAggregation> aggregationsByGraphId = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            aggregationsByGraphId.put(graphDefinition.id(), new GraphAggregation());
        }

        List<LocationDashboardImportStrategy.ImportedObservation> observations = new ArrayList<>();
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> samplePoints = new ArrayList<>();
        for (LocationDashboardAnalyzedSample analyzedSample : analyzedSamples) {
            if (analyzedSample == null || analyzedSample.sample() == null) {
                continue;
            }
            LocationDashboardImportedSample sample = analyzedSample.sample();
            observations.add(analyzedSample.toObservation());
            samplePoints.add(analyzedSample.toAnalyzedSamplePoint());

            if (sample.sublocation() == null) {
                continue;
            }
            List<GraphConfig> scopedGraphDefinitions = graphsBySublocationKey.getOrDefault(
                normalizeKey(sample.sublocation().key()),
                List.of()
            );
            for (GraphConfig graphDefinition : scopedGraphDefinitions) {
                String traceName = switch (graphDefinition.importType()) {
                    case SYSTEM_TYPE_COMPLIANCE -> sample.systemTypeName();
                    case WATER_QUALITY_COMPLIANCE -> sample.measurementName();
                };
                GraphAggregation aggregation = aggregationsByGraphId.get(graphDefinition.id());
                if (aggregation != null) {
                    aggregation.record(traceName, sample.observedDate(), analyzedSample.compliant());
                }
            }
        }

        List<LocationDashboardImportStrategy.ComputedGraphPayload> graphs = new ArrayList<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            graphs.add(new LocationDashboardImportStrategy.ComputedGraphPayload(
                graphDefinition.id(),
                buildGraphData(graphDefinition, aggregationsByGraphId.getOrDefault(graphDefinition.id(), new GraphAggregation()))
            ));
        }

        return new ObservationAggregationResult(
            List.copyOf(observations),
            List.copyOf(samplePoints),
            List.copyOf(graphs)
        );
    }

    private List<Map<String, Object>> buildGraphData(GraphConfig graphDefinition, GraphAggregation graphAggregation) {
        List<String> traceOrder = new ArrayList<>();
        if (graphDefinition.traceOrder() != null) {
            for (String traceName : graphDefinition.traceOrder()) {
                if (traceName != null && !traceName.isBlank()) {
                    traceOrder.add(traceName.strip());
                }
            }
        }
        for (String traceName : graphAggregation.traceNames()) {
            if (!containsNormalized(traceOrder, traceName)) {
                traceOrder.add(traceName);
            }
        }
        if (traceOrder.isEmpty()) {
            traceOrder.addAll(graphAggregation.traceNames());
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        int traceIndex = 0;
        boolean collapseEmptyTraces = graphDefinition.importType() == ImportType.SYSTEM_TYPE_COMPLIANCE;
        for (String traceName : traceOrder) {
            List<LocalDate> observedDates = new ArrayList<>(graphAggregation.observedDatesForTrace(traceName));
            if (observedDates.isEmpty() && collapseEmptyTraces) {
                continue;
            }
            observedDates.sort(LocalDate::compareTo);

            List<String> xValues = new ArrayList<>();
            List<Long> yValues = new ArrayList<>();
            List<Map<String, Object>> customDataValues = new ArrayList<>();
            for (LocalDate observedDate : observedDates) {
                xValues.add(observedDate.toString());
                yValues.add((long) graphAggregation.nonConforming(traceName, observedDate));
                customDataValues.add(Map.of(
                    "sampleCount", graphAggregation.total(traceName, observedDate),
                    "compliantCount", graphAggregation.compliant(traceName, observedDate),
                    "nonConformingCount", graphAggregation.nonConforming(traceName, observedDate)
                ));
            }

            String traceColor = resolveTraceColor(graphDefinition, traceName, traceIndex);
            traces.add(Map.of(
                "type", "scatter",
                "mode", "lines+markers",
                "name", traceName,
                "x", List.copyOf(xValues),
                "y", List.copyOf(yValues),
                "line", Map.of(
                    "color", traceColor,
                    "width", 2,
                    "shape", TIME_SERIES_LINE_SHAPE,
                    "smoothing", TIME_SERIES_LINE_SMOOTHING
                ),
                "marker", Map.of("size", 6),
                "customdata", List.copyOf(customDataValues)
            ));
            traceIndex += 1;
        }
        return List.copyOf(traces);
    }

    private boolean containsNormalized(List<String> values, String candidate) {
            String normalizedCandidate = normalizeKey(candidate);
        if (normalizedCandidate == null) {
            return false;
        }
        for (String value : values) {
            if (java.util.Objects.equals(
                normalizeKey(value),
                normalizedCandidate
            )) {
                return true;
            }
        }
        return false;
    }

    private String resolveTraceColor(GraphConfig graphDefinition, String traceName, int traceIndex) {
        if (graphDefinition.traceColors() != null) {
            for (Map.Entry<String, String> entry : graphDefinition.traceColors().entrySet()) {
                if (Objects.equals(normalizeKey(entry.getKey()), normalizeKey(traceName))) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_TRACE_COLORS.get(traceIndex % DEFAULT_TRACE_COLORS.size());
    }

    private static String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }

    record ObservationAggregationResult(
        List<LocationDashboardImportStrategy.ImportedObservation> observations,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples,
        List<LocationDashboardImportStrategy.ComputedGraphPayload> graphs
    ) {
    }

    private static final class GraphAggregation {
        private final Map<String, TraceCounterBucket> countersByTrace = new LinkedHashMap<>();

        void record(String traceName, LocalDate observedDate, boolean compliant) {
            String normalizedTraceName = normalizeKey(traceName);
            if (normalizedTraceName == null || observedDate == null) {
                return;
            }
            countersByTrace
                .computeIfAbsent(normalizedTraceName, ignored -> new TraceCounterBucket(traceName.strip()))
                .countersByDate()
                .computeIfAbsent(observedDate, ignored -> new ComplianceCounter())
                .record(compliant);
        }

        Set<String> traceNames() {
            return countersByTrace.values().stream()
                .map(TraceCounterBucket::displayName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        Set<LocalDate> observedDatesForTrace(String traceName) {
            TraceCounterBucket bucket = countersByTrace.get(normalizeKey(traceName));
            return bucket == null ? Set.of() : bucket.countersByDate().keySet();
        }

        int total(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = counter(traceName, observedDate);
            return counter == null ? 0 : counter.total;
        }

        int compliant(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = counter(traceName, observedDate);
            return counter == null ? 0 : counter.compliant;
        }

        int nonConforming(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = counter(traceName, observedDate);
            if (counter == null) {
                return 0;
            }
            return counter.total - counter.compliant;
        }

        private ComplianceCounter counter(String traceName, LocalDate observedDate) {
            TraceCounterBucket bucket = countersByTrace.get(normalizeKey(traceName));
            if (bucket == null) {
                return null;
            }
            return bucket.countersByDate().get(observedDate);
        }
    }

    private record TraceCounterBucket(
        String displayName,
        Map<LocalDate, ComplianceCounter> countersByDate
    ) {
        private TraceCounterBucket(String displayName) {
            this(displayName, new LinkedHashMap<>());
        }
    }

    private static final class ComplianceCounter {
        private int total;
        private int compliant;

        void record(boolean pointCompliant) {
            total += 1;
            if (pointCompliant) {
                compliant += 1;
            }
        }
    }
}
