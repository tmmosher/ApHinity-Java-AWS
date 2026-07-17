package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves a configured system profile and measurement to a location-assigned database bound. */
final class LocationDashboardMeasurementBoundResolver {
    private final Map<String, MeasurementBound> boundsByMeasurementAndType;
    private final Map<String, Set<String>> measurementNamesByProfile;

    LocationDashboardMeasurementBoundResolver(
        List<MeasurementBound> measurementBounds,
        List<LocationDashboardImportStrategyConfig.RangeProfileConfig> rangeProfiles
    ) {
        boundsByMeasurementAndType = indexBounds(measurementBounds);
        measurementNamesByProfile = indexProfiles(rangeProfiles);
    }

    MeasurementBound resolve(
        String measurementName,
        LocationDashboardImportStrategyConfig.RangeProfile profileReference
    ) {
        String profileKey = normalize(profileReference == null ? null : profileReference.value());
        if (profileKey == null) {
            return null;
        }

        if (!measurementNamesByProfile.isEmpty()) {
            Set<String> measurementNames = measurementNamesByProfile.get(profileKey);
            if (measurementNames == null) {
                return null;
            }
            if (!measurementNames.contains(normalize(measurementName))) {
                return null;
            }
        }

        String lookupKey = ConfiguredLocationDashboardImportStrategy.measurementBoundLookupKey(
            measurementName,
            profileKey
        );
        MeasurementBound bound = lookupKey == null ? null : boundsByMeasurementAndType.get(lookupKey);
        if (bound == null && !measurementNamesByProfile.isEmpty()) {
            throw new IllegalStateException(
                "No location measurement bound is configured for " + measurementName
                    + " and database type " + profileReference.value()
                    + " selected by range profile " + profileReference.value()
            );
        }
        return bound;
    }

    private Map<String, MeasurementBound> indexBounds(List<MeasurementBound> measurementBounds) {
        Map<String, MeasurementBound> indexedBounds = new LinkedHashMap<>();
        for (MeasurementBound bound : measurementBounds == null ? List.<MeasurementBound>of() : measurementBounds) {
            String key = ConfiguredLocationDashboardImportStrategy.measurementBoundLookupKey(
                bound.getMeasurementName(),
                bound.getType()
            );
            if (key == null) {
                continue;
            }
            if (indexedBounds.putIfAbsent(key, bound) != null) {
                throw new IllegalStateException(
                    "Location has duplicate measurement bounds for "
                        + bound.getMeasurementName() + " and type " + bound.getType()
                );
            }
        }
        return Map.copyOf(indexedBounds);
    }

    private Map<String, Set<String>> indexProfiles(
        List<LocationDashboardImportStrategyConfig.RangeProfileConfig> rangeProfiles
    ) {
        Map<String, Set<String>> indexedProfiles = new LinkedHashMap<>();
        for (LocationDashboardImportStrategyConfig.RangeProfileConfig profile
            : rangeProfiles == null ? List.<LocationDashboardImportStrategyConfig.RangeProfileConfig>of() : rangeProfiles) {
            Set<String> measurementNames = new LinkedHashSet<>();
            profile.measurementTypes().stream()
                .map(this::normalize)
                .forEach(measurementNames::add);
            indexedProfiles.put(normalize(profile.key()), Set.copyOf(measurementNames));
        }
        return Map.copyOf(indexedProfiles);
    }

    private String normalize(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }
}
