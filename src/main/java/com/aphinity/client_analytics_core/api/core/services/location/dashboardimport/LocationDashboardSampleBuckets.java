package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocationDashboardSampleBuckets {
    private final List<LocationDashboardAnalyzedSample> analyzedSamples = new ArrayList<>();
    private final Map<String, List<LocationDashboardAnalyzedSample>> conformingBySystemType = new LinkedHashMap<>();
    private final Map<String, List<LocationDashboardAnalyzedSample>> nonConformingBySystemType = new LinkedHashMap<>();
    private final Map<String, List<LocationDashboardAnalyzedSample>> conformingByMeasurement = new LinkedHashMap<>();
    private final Map<String, List<LocationDashboardAnalyzedSample>> nonConformingByMeasurement = new LinkedHashMap<>();

    void add(LocationDashboardImportedSample sample) {
        if (sample == null
            || sample.numericValue() == null
            || sample.measurementBound() == null
            || sample.systemType() == null) {
            return;
        }

        LocationDashboardAnalyzedSample analyzedSample = new LocationDashboardAnalyzedSample(
            sample,
            sample.systemType().rangeProfile().isCompliant(sample.numericValue(), sample.measurementBound())
        );
        analyzedSamples.add(analyzedSample);

        Map<String, List<LocationDashboardAnalyzedSample>> systemBucket = analyzedSample.compliant()
            ? conformingBySystemType
            : nonConformingBySystemType;
        Map<String, List<LocationDashboardAnalyzedSample>> measurementBucket = analyzedSample.compliant()
            ? conformingByMeasurement
            : nonConformingByMeasurement;

        append(systemBucket, sample.systemTypeName(), analyzedSample);
        append(measurementBucket, sample.measurementName(), analyzedSample);
    }

    List<LocationDashboardAnalyzedSample> analyzedSamples() {
        return List.copyOf(analyzedSamples);
    }

    Map<String, List<LocationDashboardAnalyzedSample>> conformingBySystemType() {
        return immutableBuckets(conformingBySystemType);
    }

    Map<String, List<LocationDashboardAnalyzedSample>> nonConformingBySystemType() {
        return immutableBuckets(nonConformingBySystemType);
    }

    Map<String, List<LocationDashboardAnalyzedSample>> conformingByMeasurement() {
        return immutableBuckets(conformingByMeasurement);
    }

    Map<String, List<LocationDashboardAnalyzedSample>> nonConformingByMeasurement() {
        return immutableBuckets(nonConformingByMeasurement);
    }

    private void append(
        Map<String, List<LocationDashboardAnalyzedSample>> bucket,
        String key,
        LocationDashboardAnalyzedSample analyzedSample
    ) {
        if (key == null || key.isBlank()) {
            return;
        }
        bucket.computeIfAbsent(key.strip(), ignored -> new ArrayList<>()).add(analyzedSample);
    }

    private Map<String, List<LocationDashboardAnalyzedSample>> immutableBuckets(
        Map<String, List<LocationDashboardAnalyzedSample>> bucket
    ) {
        Map<String, List<LocationDashboardAnalyzedSample>> immutableBucket = new LinkedHashMap<>();
        bucket.forEach((key, value) -> immutableBucket.put(key, List.copyOf(value)));
        return Map.copyOf(immutableBucket);
    }
}
