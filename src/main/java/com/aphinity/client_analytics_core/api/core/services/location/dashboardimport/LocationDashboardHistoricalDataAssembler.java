package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType;

/**
 * Reconstructs historical raw inputs for derived dashboard graphs.
 * Water-quality compliance graphs are the canonical sample source to avoid
 * double-counting the same physical sample through multiple system-type projections.
 */
final class LocationDashboardHistoricalDataAssembler {
    private final LocationDashboardCorrectiveActionService correctiveActionService;

    LocationDashboardHistoricalDataAssembler(LocationDashboardCorrectiveActionService correctiveActionService) {
        this.correctiveActionService = correctiveActionService;
    }

    LocationDashboardDerivedGraphSupport.HistoricalDerivedData buildHistoricalDerivedData(
        List<GraphConfig> graphDefinitions,
        Map<String, Graph> matchedImportGraphsByDefinitionId,
        Map<Long, Graph> assignedGraphsById,
        Map<Long, Graph> previewGraphsById,
        List<LocationDashboardImportStrategy.ImportedObservation> importedObservations,
        List<ServiceEvent> effectiveCorrectiveActions
    ) {
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            if (graphDefinition.importType() != ImportType.WATER_QUALITY_COMPLIANCE) {
                continue;
            }

            Graph matchedGraph = matchedImportGraphsByDefinitionId.get(
                LocationDashboardGraphMetadataSupport.normalizeKey(graphDefinition.id())
            );
            if (matchedGraph == null || matchedGraph.getId() == null) {
                continue;
            }
            Graph effectiveGraph = previewGraphsById.getOrDefault(
                matchedGraph.getId(),
                assignedGraphsById.get(matchedGraph.getId())
            );
            if (effectiveGraph == null) {
                continue;
            }

            String facilityName = LocationDashboardGraphMetadataSupport.firstNonBlank(
                LocationDashboardGraphMetadataSupport.readImportMetadata(effectiveGraph).get("graphTitle"),
                graphDefinition.title(),
                LocationDashboardGraphMetadataSupport.readGraphLayoutTitleText(effectiveGraph)
            );
            collectPersistedSamplePoints(samplePointsByIdentity, effectiveGraph, facilityName);
        }

        mergeImportedObservationSamplePoints(samplePointsByIdentity, importedObservations);

        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> samplesByDate = new LinkedHashMap<>();
        samplePointsByIdentity.values().stream()
            .sorted(Comparator
                .comparing(LocationDashboardDerivedGraphSupport.HistoricalSamplePoint::observedDate)
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.facilityName()))
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.measurementName())))
            .forEach(samplePoint -> samplesByDate
                .computeIfAbsent(samplePoint.observedDate(), ignored -> new ArrayList<>())
                .add(samplePoint));

        List<LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction> correctiveActions = effectiveCorrectiveActions.stream()
            .map(correctiveActionService::toHistoricalCorrectiveAction)
            .filter(Objects::nonNull)
            .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(samplesByDate, correctiveActions);
    }

    private void collectPersistedSamplePoints(
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity,
        Graph effectiveGraph,
        String facilityName
    ) {
        if (effectiveGraph == null || effectiveGraph.getGraphTraces() == null) {
            return;
        }
        for (GraphTrace trace : effectiveGraph.getGraphTraces()) {
            if (!isSupportedHistoricalTrace(trace)) {
                continue;
            }
            String measurementName = traceName(trace);
            if (measurementName == null || trace.getTimeSeriesPoints() == null) {
                continue;
            }
            List<?> legacyCustomDataValues = validatedLegacyCustomData(trace, trace.getTimeSeriesPoints().size());
            for (int index = 0; index < trace.getTimeSeriesPoints().size(); index += 1) {
                GraphTimeSeriesPoint point = trace.getTimeSeriesPoints().get(index);
                if (point == null) {
                    continue;
                }
                LocalDate observedDate = observedDate(point);
                if (observedDate == null) {
                    continue;
                }
                Map<String, Object> pointMetadata = pointMetadata(point, legacyCustomDataValues, index);
                long sampleCount = LocationDashboardGraphMetadataSupport.asLong(pointMetadata.get("sampleCount"));
                long compliantCount = LocationDashboardGraphMetadataSupport.asLong(pointMetadata.get("compliantCount"));
                if (sampleCount <= 0L) {
                    continue;
                }

                String sampleIdentity = observedDate
                    + "|"
                    + LocationDashboardGraphMetadataSupport.nullSafeNormalized(facilityName)
                    + "|"
                    + LocationDashboardGraphMetadataSupport.nullSafeNormalized(measurementName);
                samplePointsByIdentity.put(sampleIdentity, new LocationDashboardDerivedGraphSupport.HistoricalSamplePoint(
                    observedDate,
                    facilityName,
                    measurementName,
                    sampleCount,
                    Math.max(0L, Math.min(compliantCount, sampleCount))
                ));
            }
        }
    }

    private boolean isSupportedHistoricalTrace(GraphTrace trace) {
        if (trace == null) {
            return false;
        }
        String normalizedTraceType = LocationDashboardGraphMetadataSupport.normalizeKey(trace.getTraceType());
        String normalizedDataMode = LocationDashboardGraphMetadataSupport.normalizeKey(trace.getDataMode());
        return (Objects.equals(normalizedTraceType, "scatter") || Objects.equals(normalizedTraceType, "scattergl"))
            && Objects.equals(normalizedDataMode, "time series");
    }

    private String traceName(GraphTrace trace) {
        String traceName = trace == null ? null : trace.getTraceName();
        if (traceName == null || traceName.isBlank()) {
            return null;
        }
        return traceName;
    }

    private LocalDate observedDate(GraphTimeSeriesPoint point) {
        if (point == null) {
            return null;
        }
        Map<String, Object> pointMeta = LocationDashboardGraphMetadataSupport.asMap(point.getPointMeta());
        LocalDate explicitDate = LocationDashboardGraphMetadataSupport.parseLocalDate(pointMeta.get("x"));
        if (explicitDate != null) {
            return explicitDate;
        }
        return point.getObservedAt() == null
            ? null
            : point.getObservedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private Map<String, Object> pointMetadata(GraphTimeSeriesPoint point, List<?> legacyCustomDataValues, int pointIndex) {
        Map<String, Object> pointMeta = LocationDashboardGraphMetadataSupport.asMap(point == null ? null : point.getPointMeta());
        Map<String, Object> customData = LocationDashboardGraphMetadataSupport.asMap(pointMeta.get("customdata"));
        if (!customData.isEmpty()) {
            return customData;
        }
        if (pointIndex < 0 || pointIndex >= legacyCustomDataValues.size()) {
            return Map.of();
        }
        Map<String, Object> legacyCustomData = LocationDashboardGraphMetadataSupport.asMap(legacyCustomDataValues.get(pointIndex));
        return legacyCustomData.isEmpty() ? Map.of() : legacyCustomData;
    }

    private List<?> validatedLegacyCustomData(GraphTrace trace, int expectedPointCount) {
        Map<String, Object> traceConfig = LocationDashboardGraphMetadataSupport.asMap(trace == null ? null : trace.getTraceConfig());
        List<?> customDataValues = LocationDashboardGraphMetadataSupport.asList(traceConfig.get("customdata"));
        return customDataValues.size() == expectedPointCount ? customDataValues : List.of();
    }

    private void mergeImportedObservationSamplePoints(
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity,
        List<LocationDashboardImportStrategy.ImportedObservation> importedObservations
    ) {
        Map<String, ImportedObservationAggregate> importedObservationsByIdentity = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.ImportedObservation observation : importedObservations) {
            if (observation == null
                || observation.observedDate() == null
                || observation.facilityName() == null
                || observation.measurementName() == null) {
                continue;
            }

            String sampleIdentity = observation.observedDate()
                + "|"
                + LocationDashboardGraphMetadataSupport.nullSafeNormalized(observation.facilityName())
                + "|"
                + LocationDashboardGraphMetadataSupport.nullSafeNormalized(observation.measurementName());
            importedObservationsByIdentity
                .computeIfAbsent(
                    sampleIdentity,
                    ignored -> new ImportedObservationAggregate(
                        observation.observedDate(),
                        observation.facilityName(),
                        observation.measurementName()
                    )
                )
                .record(observation.compliant());
        }

        importedObservationsByIdentity.forEach((sampleIdentity, aggregate) ->
            samplePointsByIdentity.put(sampleIdentity, aggregate.toHistoricalSamplePoint())
        );
    }

    private static final class ImportedObservationAggregate {
        private final LocalDate observedDate;
        private final String facilityName;
        private final String measurementName;
        private long sampleCount;
        private long compliantCount;

        private ImportedObservationAggregate(LocalDate observedDate, String facilityName, String measurementName) {
            this.observedDate = observedDate;
            this.facilityName = facilityName;
            this.measurementName = measurementName;
        }

        void record(boolean compliant) {
            sampleCount += 1L;
            if (compliant) {
                compliantCount += 1L;
            }
        }

        LocationDashboardDerivedGraphSupport.HistoricalSamplePoint toHistoricalSamplePoint() {
            return new LocationDashboardDerivedGraphSupport.HistoricalSamplePoint(
                observedDate,
                facilityName,
                measurementName,
                sampleCount,
                compliantCount
            );
        }
    }
}
