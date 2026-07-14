package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardDerivedGraphSupport.HistoricalNonConformance;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategy.AnalyzedSamplePoint;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples,
        List<ServiceEvent> effectiveCorrectiveActions,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            if (graphDefinition.importType() != ImportType.WATER_QUALITY_COMPLIANCE
                && graphDefinition.importType() != ImportType.SYSTEM_TYPE_COMPLIANCE) {
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
            collectPersistedSamplePoints(samplePointsByIdentity, effectiveGraph, facilityName, graphDefinition.importType());
        }

        mergeImportedSamplePoints(samplePointsByIdentity, analyzedSamples);

        Map<LocalDate, List<LocationDashboardDerivedGraphSupport.HistoricalSamplePoint>> samplesByDate = new LinkedHashMap<>();
        samplePointsByIdentity.values().stream()
            .sorted(Comparator
                .comparing(LocationDashboardDerivedGraphSupport.HistoricalSamplePoint::observedDate)
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.facilityName()))
                .thenComparing(samplePoint -> LocationDashboardGraphMetadataSupport.nullSafeNormalized(samplePoint.measurementName())))
            .forEach(samplePoint -> samplesByDate
                .computeIfAbsent(samplePoint.observedDate(), ignored -> new ArrayList<>())
                .add(samplePoint));

        List<HistoricalCorrectiveAction> correctiveActions = effectiveCorrectiveActions.stream()
            .map(correctiveActionService::toHistoricalCorrectiveAction)
            .filter(Objects::nonNull)
            .toList();

        return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(
            samplesByDate,
            mergeHistoricalNonConformances(analyzedSamples, correctiveActions),
            collectRawSamples(analyzedSamples, identityPattern)
        );
    }

    private List<LocationDashboardDerivedGraphSupport.HistoricalRawSample> collectRawSamples(
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        if (analyzedSamples == null || analyzedSamples.isEmpty()) {
            return List.of();
        }
        List<LocationDashboardDerivedGraphSupport.HistoricalRawSample> rawSamples = new ArrayList<>();
        for (AnalyzedSamplePoint analyzedSample : analyzedSamples) {
            if (analyzedSample == null
                || analyzedSample.origin() == LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT
                || analyzedSample.observedDate() == null
                || analyzedSample.measurementName() == null) {
                continue;
            }
            Map<String, String> identityValues = identityValues(analyzedSample, identityPattern);
            String rowIdentifier = rowIdentifier(identityValues, analyzedSample);
            if (rowIdentifier == null) {
                continue;
            }
            rawSamples.add(new LocationDashboardDerivedGraphSupport.HistoricalRawSample(
                analyzedSample.observedDate(),
                rowIdentifier,
                identityValues,
                analyzedSample.measurementName(),
                analyzedSample.rawValue(),
                analyzedSample.units(),
                analyzedSample.compliant(),
                analyzedSample.resolved()
            ));
        }
        return List.copyOf(rawSamples);
    }

    private Map<String, String> identityValues(
        AnalyzedSamplePoint sample,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        return identityValuesFromSampleIdentity(sample, identityPattern);
    }

    private Map<String, String> identityValuesFromSampleIdentity(
        AnalyzedSamplePoint sample,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        if (sample == null || sample.sampleIdentity() == null || sample.sampleIdentity().isBlank()) {
            return Map.of();
        }
        List<String> tokens = List.of(sample.sampleIdentity().split("\\|", -1));
        Map<String, Integer> tokenIndexesByIdentityColumn = sampleIdentityIndexes(sample.sampleIdentity());
        Map<String, String> values = new LinkedHashMap<>();
        for (LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn identityColumn : effectiveIdentityPattern(identityPattern)) {
            if (identityColumn == null
                || identityColumn.identityKey() == null
                || identityColumn.identityKey().isBlank()) {
                continue;
            }
            Integer tokenIndex = tokenIndexesByIdentityColumn.get(identityColumn.identityKey());
            if (tokenIndex == null || tokenIndex < 0 || tokenIndex >= tokens.size()) {
                continue;
            }
            putIfPresent(values, identityColumn.identityKey(), tokens.get(tokenIndex));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private Map<String, Integer> sampleIdentityIndexes(String sampleIdentity) {
        if (sampleIdentity == null) {
            return Map.of();
        }
        if (sampleIdentity.startsWith(LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX)) {
            return generatedSampleIdentityIndexes();
        }
        if (sampleIdentity.startsWith("primary-sample|")
            || sampleIdentity.startsWith("supplemental-sample-")) {
            return commentSampleIdentityIndexes();
        }
        if (sampleIdentity.startsWith("observation|")) {
            return observationSampleIdentityIndexes();
        }
        return worksheetSampleIdentityIndexes();
    }

    private List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> effectiveIdentityPattern(
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        return identityPattern == null || identityPattern.isEmpty()
            ? List.of(
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("facility", List.of()),
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("building", List.of()),
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("system", List.of()),
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("pointOfUse", List.of()),
                new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("basis", List.of())
            )
            : identityPattern;
    }

    private Map<String, Integer> worksheetSampleIdentityIndexes() {
        return Map.of(
            "facility", 0,
            "building", 1,
            "system", 2,
            "pointOfUse", 4,
            "basis", 5
        );
    }

    private Map<String, Integer> generatedSampleIdentityIndexes() {
        return Map.of(
            "facility", 2,
            "building", 3,
            "system", 4,
            "pointOfUse", 7,
            "basis", 8
        );
    }

    private Map<String, Integer> commentSampleIdentityIndexes() {
        return Map.of(
            "facility", 1,
            "building", 2,
            "system", 3,
            "pointOfUse", 5,
            "basis", 6
        );
    }

    private Map<String, Integer> observationSampleIdentityIndexes() {
        return Map.of(
            "facility", 2
        );
    }

    private void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value.strip());
        }
    }

    private String rowIdentifier(Map<String, String> identityValues, AnalyzedSamplePoint sample) {
        if ((identityValues == null || identityValues.isEmpty()) && (sample == null || sample.sampleIdentity() == null)) {
            return null;
        }
        List<String> identityParts = new ArrayList<>();
        if (identityValues != null) {
            identityValues.values().stream()
                .map(LocationDashboardGraphMetadataSupport::nullSafeNormalized)
                .forEach(identityParts::add);
        }
        identityParts.add(LocationDashboardGraphMetadataSupport.nullSafeNormalized(sample == null ? null : sample.measurementName()));
        if (identityParts.stream().allMatch(String::isBlank) && sample != null) {
            identityParts.add(LocationDashboardGraphMetadataSupport.nullSafeNormalized(sample.sampleIdentity()));
        }
        return String.join("|", identityParts);
    }

    private void collectPersistedSamplePoints(
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity,
        Graph effectiveGraph,
        String facilityName,
        ImportType importType
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

                String measurementLabel = importType == ImportType.WATER_QUALITY_COMPLIANCE ? measurementName : null;
                String systemTypeLabel = importType == ImportType.SYSTEM_TYPE_COMPLIANCE ? measurementName : null;
                String sampleIdentity = sampleIdentity(observedDate, facilityName, measurementLabel, systemTypeLabel);
                samplePointsByIdentity.put(sampleIdentity, new LocationDashboardDerivedGraphSupport.HistoricalSamplePoint(
                    observedDate,
                    facilityName,
                    measurementLabel,
                    systemTypeLabel,
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

    private void mergeImportedSamplePoints(
        Map<String, LocationDashboardDerivedGraphSupport.HistoricalSamplePoint> samplePointsByIdentity,
        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples
    ) {
        Map<String, ImportedSampleAggregate> importedSamplesByIdentity = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample : analyzedSamples) {
            if (analyzedSample == null
                || analyzedSample.observedDate() == null
                || analyzedSample.facilityName() == null
                || analyzedSample.measurementName() == null
                || analyzedSample.origin() == LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT) {
                continue;
            }

            String measurementSampleIdentity = sampleIdentity(
                analyzedSample.observedDate(),
                analyzedSample.facilityName(),
                analyzedSample.measurementName(),
                null
            );
            importedSamplesByIdentity
                .computeIfAbsent(
                    measurementSampleIdentity,
                    ignored -> new ImportedSampleAggregate(
                        analyzedSample.observedDate(),
                        analyzedSample.facilityName(),
                        analyzedSample.measurementName(),
                        null
                    )
                )
                .record(analyzedSample.compliant());

            if (analyzedSample.systemTypeName() != null && !analyzedSample.systemTypeName().isBlank()) {
                String systemTypeSampleIdentity = sampleIdentity(
                    analyzedSample.observedDate(),
                    analyzedSample.facilityName(),
                    null,
                    analyzedSample.systemTypeName()
                );
                importedSamplesByIdentity
                    .computeIfAbsent(
                        systemTypeSampleIdentity,
                        ignored -> new ImportedSampleAggregate(
                            analyzedSample.observedDate(),
                            analyzedSample.facilityName(),
                            null,
                            analyzedSample.systemTypeName()
                        )
                    )
                    .record(analyzedSample.compliant());
            }
        }

        importedSamplesByIdentity.forEach((sampleIdentity, aggregate) ->
            samplePointsByIdentity.put(sampleIdentity, aggregate.toHistoricalSamplePoint())
        );
    }

    private String sampleIdentity(
        LocalDate observedDate,
        String facilityName,
        String measurementName,
        String systemTypeName
    ) {
        return observedDate
            + "|"
            + LocationDashboardGraphMetadataSupport.nullSafeNormalized(facilityName)
            + "|"
            + LocationDashboardGraphMetadataSupport.nullSafeNormalized(measurementName)
            + "|"
            + LocationDashboardGraphMetadataSupport.nullSafeNormalized(systemTypeName);
    }

    private List<HistoricalNonConformance> mergeHistoricalNonConformances(
        List<AnalyzedSamplePoint> analyzedSamples,
        List<HistoricalCorrectiveAction> correctiveActions
    ) {
        Map<String, HistoricalNonConformance> nonConformancesByIdentity =
            new LinkedHashMap<>();
        Map<String, Integer> sampleIncidentOrdinalsByIdentity = new LinkedHashMap<>();

        for (AnalyzedSamplePoint analyzedSample : analyzedSamples) {
            if (analyzedSample == null || !analyzedSample.nonConforming()) {
                continue;
            }
            if (analyzedSample.origin() == LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT) {
                continue;
            }
            String identity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                analyzedSample.measurementName(),
                analyzedSample.observedDate(),
                analyzedSample.facilityName(),
                analyzedSample.buildingName(),
                analyzedSample.systemName(),
                analyzedSample.pointOfUse(),
                analyzedSample.basis(),
                analyzedSample.sampleIdentity()
            );
            if (identity == null) {
                continue;
            }
            String incidentIdentity = sampleIncidentIdentity(
                identity,
                analyzedSample,
                sampleIncidentOrdinalsByIdentity
            );
            HistoricalNonConformance analyzedNonConformance =
                new LocationDashboardDerivedGraphSupport.HistoricalNonConformance(
                    analyzedSample.observedDate(),
                    analyzedSample.facilityName(),
                    analyzedSample.buildingName(),
                    analyzedSample.systemName(),
                    analyzedSample.measurementName(),
                    analyzedSample.pointOfUse(),
                    analyzedSample.basis(),
                    analyzedSample.sampleIdentity(),
                    analyzedSample.resolved(),
                    analyzedSample.turnaroundDays()
                );
            nonConformancesByIdentity.merge(
                incidentIdentity,
                analyzedNonConformance,
                HistoricalNonConformance::merge
            );
        }

        for (HistoricalCorrectiveAction correctiveAction : correctiveActions) {
            if (correctiveAction == null) {
                continue;
            }
            String identity = correctiveAction.identityKey();
            if (identity == null || !nonConformancesByIdentity.containsKey(identity)) {
                continue;
            }
            nonConformancesByIdentity.merge(
                identity,
                correctiveAction.toHistoricalNonConformance(),
                LocationDashboardDerivedGraphSupport.HistoricalNonConformance::merge
            );
        }

        return List.copyOf(nonConformancesByIdentity.values());
    }

    private String sampleIncidentIdentity(
        String baseIdentity,
        AnalyzedSamplePoint analyzedSample,
        Map<String, Integer> sampleIncidentOrdinalsByIdentity
    ) {
        if (hasExplicitSampleIdentity(analyzedSample)) {
            return baseIdentity;
        }
        int ordinal = sampleIncidentOrdinalsByIdentity.merge(baseIdentity, 1, Integer::sum);
        return ordinal == 1 ? baseIdentity : baseIdentity + "|incident|" + ordinal;
    }

    private boolean hasExplicitSampleIdentity(AnalyzedSamplePoint analyzedSample) {
        return analyzedSample != null
            && analyzedSample.sampleIdentity() != null
            && !analyzedSample.sampleIdentity().isBlank()
            && !analyzedSample.sampleIdentity().startsWith(
                LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX
            );
    }

    private static final class ImportedSampleAggregate {
        private final LocalDate observedDate;
        private final String facilityName;
        private final String measurementName;
        private final String systemTypeName;
        private long sampleCount;
        private long compliantCount;

        private ImportedSampleAggregate(
            LocalDate observedDate,
            String facilityName,
            String measurementName,
            String systemTypeName
        ) {
            this.observedDate = observedDate;
            this.facilityName = facilityName;
            this.measurementName = measurementName;
            this.systemTypeName = systemTypeName;
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
                systemTypeName,
                sampleCount,
                compliantCount
            );
        }
    }
}
