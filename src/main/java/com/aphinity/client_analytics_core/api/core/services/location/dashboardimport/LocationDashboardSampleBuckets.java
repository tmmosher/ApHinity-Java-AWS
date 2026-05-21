package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocationDashboardSampleBuckets {
    private final List<LocationDashboardAnalyzedSample> analyzedSamples = new ArrayList<>();
    private final Map<ResolutionBucketKey, ResolutionBucketLeaf> resolutionLeavesByKey = new LinkedHashMap<>();
    private boolean resolutionAnalysisComplete;

    void add(LocationDashboardImportedSample sample) {
        if (sample == null
            || sample.numericValue() == null
            || sample.measurementBound() == null
            || sample.systemType() == null
            || sample.systemType().rangeProfile() == null) {
            return;
        }

        LocationDashboardAnalyzedSample analyzedSample = new LocationDashboardAnalyzedSample(
            sample,
            sample.systemType().rangeProfile().isCompliant(sample.numericValue(), sample.measurementBound())
        );
        int analyzedSampleIndex = analyzedSamples.size();
        analyzedSamples.add(analyzedSample);
        ResolutionBucketKey resolutionBucketKey = ResolutionBucketKey.maybeFrom(sample);
        if (resolutionBucketKey != null) {
            resolutionLeavesByKey
                .computeIfAbsent(resolutionBucketKey, ignored -> new ResolutionBucketLeaf())
                .append(analyzedSampleIndex, analyzedSample.compliant());
        }
        resolutionAnalysisComplete = false;
    }

    List<LocationDashboardAnalyzedSample> analyzedSamples() {
        ensureResolutionAnalysis();
        return List.copyOf(analyzedSamples);
    }

    private void ensureResolutionAnalysis() {
        if (resolutionAnalysisComplete) {
            return;
        }
        resolutionLeavesByKey.values().forEach(this::analyzeResolutionLeaf);
        resolutionAnalysisComplete = true;
    }

    private void analyzeResolutionLeaf(ResolutionBucketLeaf leaf) {
        if (leaf == null || leaf.conformingIndexes().isEmpty() || leaf.nonConformingIndexes().isEmpty()) {
            return;
        }

        List<LocalDate> sortedConformingDates = leaf.conformingIndexes().stream()
            .map(this::resolutionAnchorDate)
            .filter(date -> date != null)
            .sorted(Comparator.naturalOrder())
            .toList();
        if (sortedConformingDates.isEmpty()) {
            return;
        }

        for (Integer nonConformingIndex : leaf.nonConformingIndexes()) {
            if (nonConformingIndex == null || nonConformingIndex < 0 || nonConformingIndex >= analyzedSamples.size()) {
                continue;
            }
            LocationDashboardAnalyzedSample analyzedSample = analyzedSamples.get(nonConformingIndex);
            LocalDate resolutionAnchorDate = analyzedSample.sample() == null
                ? null
                : analyzedSample.sample().resolutionAnchorDate();
            if (resolutionAnchorDate == null) {
                continue;
            }

            LocalDate resolvedAt = firstDateAfter(sortedConformingDates, resolutionAnchorDate);
            if (resolvedAt == null) {
                continue;
            }
            long turnaroundDays = Math.max(0L, ChronoUnit.DAYS.between(resolutionAnchorDate, resolvedAt));
            analyzedSamples.set(nonConformingIndex, analyzedSample.withResolution(true, turnaroundDays));
        }
    }

    private LocalDate firstDateAfter(List<LocalDate> sortedDates, LocalDate observedDate) {
        if (sortedDates == null || sortedDates.isEmpty() || observedDate == null) {
            return null;
        }
        int lowerBound = 0;
        int upperBound = sortedDates.size();
        while (lowerBound < upperBound) {
            int midpoint = (lowerBound  + upperBound) >>> 1; // random use of bitwise ops ? division would have sufficed here and probably been optimized to bitwise op by jvm anyway
            LocalDate candidateDate = sortedDates.get(midpoint);
            if (candidateDate == null || !candidateDate.isAfter(observedDate)) {
                lowerBound = midpoint + 1;
            } else {
                upperBound = midpoint;
            }
        }
        return lowerBound >= sortedDates.size() ? null : sortedDates.get(lowerBound);
    }

    private LocalDate resolutionAnchorDate(Integer analyzedSampleIndex) {
        if (analyzedSampleIndex == null || analyzedSampleIndex < 0 || analyzedSampleIndex >= analyzedSamples.size()) {
            return null;
        }
        LocationDashboardImportedSample sample = analyzedSamples.get(analyzedSampleIndex).sample();
        return sample == null ? null : sample.resolutionAnchorDate();
    }

    private record ResolutionBucketKey(
        String facilityName,
        String buildingName,
        String systemName,
        String measurementName,
        String pointOfUse,
        String basis
    ) {
        private static ResolutionBucketKey maybeFrom(LocationDashboardImportedSample sample) {
            String facilityName = requiredNormalized(sample == null ? null : sample.facilityName());
            String buildingName = optionalNormalized(sample == null ? null : sample.resolutionBuildingName());
            String systemName = requiredNormalized(sample == null ? null : sample.resolutionSystemName());
            String measurementName = requiredNormalized(sample == null ? null : sample.measurementName());
            String pointOfUse = requiredNormalized(sample == null ? null : sample.pointOfUse());
            String basis = requiredNormalized(sample == null ? null : sample.basis());
            if (facilityName == null
                || systemName == null
                || measurementName == null
                || pointOfUse == null
                ) {
                return null;
            }
            return new ResolutionBucketKey(
                facilityName,
                buildingName,
                systemName,
                measurementName,
                pointOfUse,
                basis
            );
        }

        private static String requiredNormalized(String value) {
            return LocationDashboardGraphMetadataSupport.normalizeKey(value);
        }

        private static String optionalNormalized(String value) {
            return LocationDashboardGraphMetadataSupport.normalizeKey(value);
        }
    }

    private static final class ResolutionBucketLeaf {
        private final List<Integer> conformingIndexes = new ArrayList<>();
        private final List<Integer> nonConformingIndexes = new ArrayList<>();

        void append(int analyzedSampleIndex, boolean compliant) {
            if (compliant) {
                conformingIndexes.add(analyzedSampleIndex);
            } else {
                nonConformingIndexes.add(analyzedSampleIndex);
            }
        }

        List<Integer> conformingIndexes() {
            return conformingIndexes;
        }

        List<Integer> nonConformingIndexes() {
            return nonConformingIndexes;
        }
    }
}
