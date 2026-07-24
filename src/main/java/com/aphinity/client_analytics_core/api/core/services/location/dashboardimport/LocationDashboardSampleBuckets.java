package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.*;

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
            sample.measurementBound().isCompliant(sample.numericValue())
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
            .filter(Objects::nonNull)
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

            LocalDate conformanceRestoredDate = firstDateAfter(sortedConformingDates, resolutionAnchorDate);
            if (conformanceRestoredDate == null) {
                continue;
            }
            analyzedSamples.set(
                nonConformingIndex,
                analyzedSample.withResolution(new ConformanceResolution(
                    resolutionAnchorDate,
                    conformanceRestoredDate
                ))
            );
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
        String measurementName,
        String identity
    ) {
        private static ResolutionBucketKey maybeFrom(LocationDashboardImportedSample sample) {
            String measurementName = requiredNormalized(sample == null ? null : sample.measurementName());
            String identity = sample == null
                ? null
                : LocationDashboardIdentitySupport.normalizedIdentity(sample.resolutionIdentityValues());
            if (measurementName == null
                || identity == null
                || identity.isBlank()) {
                return null;
            }
            return new ResolutionBucketKey(
                measurementName,
                identity
            );
        }

        private static String requiredNormalized(String value) {
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
