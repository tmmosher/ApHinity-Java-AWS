package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;

import java.time.LocalDate;
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
        Map<Long, Graph> lockedGraphsById,
        Map<Long, Graph> graphsToPersistById,
        List<ServiceEvent> persistedCorrectiveActions
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
            Graph effectiveGraph = graphsToPersistById.getOrDefault(
                matchedGraph.getId(),
                lockedGraphsById.get(matchedGraph.getId())
            );
            if (effectiveGraph == null) {
                continue;
            }

            String facilityName = LocationDashboardGraphMetadataSupport.firstNonBlank(
                LocationDashboardGraphMetadataSupport.readImportMetadata(effectiveGraph).get("graphTitle"),
                graphDefinition.title(),
                LocationDashboardGraphMetadataSupport.readGraphLayoutTitleText(effectiveGraph)
            );
            for (Map<String, Object> trace : LocationDashboardGraphMetadataSupport.currentTraceList(effectiveGraph)) {
                String measurementName = traceName(trace);
                List<?> xValues = LocationDashboardGraphMetadataSupport.asList(trace.get("x"));
                List<?> customDataValues = LocationDashboardGraphMetadataSupport.asList(trace.get("customdata"));
                for (int index = 0; index < xValues.size(); index += 1) {
                    LocalDate observedDate = LocationDashboardGraphMetadataSupport.parseLocalDate(xValues.get(index));
                    if (observedDate == null) {
                        continue;
                    }
                    Map<String, Object> pointMetadata = index < customDataValues.size()
                        ? LocationDashboardGraphMetadataSupport.asMap(customDataValues.get(index))
                        : Map.of();
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

        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> samplesByDate = new LinkedHashMap<>();
        samplePointsByIdentity.values().stream()
            .sorted(Comparator
                .comparing(LocationDashboardDerivedGraphSupport.HistoricalSamplePoint::observedDate)
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.facilityName()))
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.measurementName())))
            .forEach(samplePoint -> samplesByDate
                .computeIfAbsent(samplePoint.observedDate(), ignored -> new ArrayList<>())
                .add(samplePoint));

        List<LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction> correctiveActions = persistedCorrectiveActions.stream()
            .map(correctiveActionService::toHistoricalCorrectiveAction)
            .filter(Objects::nonNull)
            .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(samplesByDate, correctiveActions);
    }

    private String traceName(Map<String, Object> trace) {
        Object rawName = trace.get("name");
        if (!(rawName instanceof String traceName) || traceName.isBlank()) {
            return null;
        }
        return traceName;
    }
}
