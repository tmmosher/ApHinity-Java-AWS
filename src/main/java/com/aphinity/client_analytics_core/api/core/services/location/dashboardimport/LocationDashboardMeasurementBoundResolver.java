package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves a configured system profile and measurement to a location-assigned database bound. */
final class LocationDashboardMeasurementBoundResolver {
    private final Map<String, MeasurementBound> boundsByMeasurementAndType;
    private final Map<String, Map<String, String>> measurementTypesByProfile;

    LocationDashboardMeasurementBoundResolver(
        List<MeasurementBound> measurementBounds,
        List<LocationDashboardImportStrategyConfig.RangeProfileConfig> rangeProfiles
    ) {
        boundsByMeasurementAndType = indexBounds(measurementBounds);
        measurementTypesByProfile = indexProfiles(rangeProfiles);
    }

    MeasurementBound resolve(
        String measurementName,
        LocationDashboardImportStrategyConfig.RangeProfile profileReference
    ) {
        String profileKey = normalize(profileReference == null ? null : profileReference.value());
        if (profileKey == null) {
            return null;
        }

        String boundType = profileKey;
        if (!measurementTypesByProfile.isEmpty()) {
            Map<String, String> measurementTypes = measurementTypesByProfile.get(profileKey);
            if (measurementTypes == null) {
                return null;
            }
            boundType = measurementTypes.get(normalize(measurementName));
            if (boundType == null) {
                return null;
            }
        }

        String lookupKey = ConfiguredLocationDashboardImportStrategy.measurementBoundLookupKey(
            measurementName,
            boundType
        );
        MeasurementBound bound = lookupKey == null ? null : boundsByMeasurementAndType.get(lookupKey);
        if (bound == null && !measurementTypesByProfile.isEmpty()) {
            throw new IllegalStateException(
                "No location measurement bound is configured for " + measurementName
                    + " and database type " + boundType
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

    private Map<String, Map<String, String>> indexProfiles(
        List<LocationDashboardImportStrategyConfig.RangeProfileConfig> rangeProfiles
    ) {
        Map<String, Map<String, String>> indexedProfiles = new LinkedHashMap<>();
        for (LocationDashboardImportStrategyConfig.RangeProfileConfig profile
            : rangeProfiles == null ? List.<LocationDashboardImportStrategyConfig.RangeProfileConfig>of() : rangeProfiles) {
            Map<String, String> measurementTypes = new LinkedHashMap<>();
            profile.measurementTypes().forEach((measurementName, type) ->
                measurementTypes.put(normalize(measurementName), normalize(type))
            );
            indexedProfiles.put(normalize(profile.key()), Map.copyOf(measurementTypes));
        }
        return Map.copyOf(indexedProfiles);
    }

    private String normalize(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }
}
