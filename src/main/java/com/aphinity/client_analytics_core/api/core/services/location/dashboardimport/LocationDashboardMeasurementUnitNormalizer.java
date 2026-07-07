package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LocationDashboardMeasurementUnitNormalizer {
    private final Map<String, String> unitsByMeasurementName;

    LocationDashboardMeasurementUnitNormalizer(
        List<LocationDashboardImportStrategyConfig.MeasurementUnitConfig> configuredUnits
    ) {
        Map<String, String> units = new LinkedHashMap<>();
        for (LocationDashboardImportStrategyConfig.MeasurementUnitConfig configuredUnit
            : configuredUnits == null ? List.<LocationDashboardImportStrategyConfig.MeasurementUnitConfig>of() : configuredUnits) {
            if (configuredUnit == null) {
                continue;
            }
            String canonicalUnit = configuredUnit.value() == null ? "" : configuredUnit.value().strip();
            for (String measurementName : configuredUnit.forMeasurementNames()) {
                if (measurementName != null && !measurementName.isBlank()) {
                    units.put(normalizeKey(measurementName), canonicalUnit);
                }
            }
        }
        this.unitsByMeasurementName = Map.copyOf(units);
    }

    String forMeasurementName(String measurementName) {
        String unit = unitsByMeasurementName.get(normalizeKey(measurementName));
        return unit == null || unit.isBlank() ? null : unit;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
    }
}
