package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocationDashboardIdentityFixtures {
    private LocationDashboardIdentityFixtures() {
    }

    static LocationDashboardSpreadsheetParser.ParsedDashboardRow parsedRow(
        int rowNumber,
        String facility,
        String building,
        String system,
        String pointOfUse,
        String basis,
        List<LocationDashboardSpreadsheetParser.ParsedDashboardCell> cells
    ) {
        return new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
            rowNumber,
            identityValues(facility, building, system, pointOfUse, basis),
            cells
        );
    }

    static Map<String, String> identityValues(
        String facility,
        String building,
        String system,
        String pointOfUse,
        String basis
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "facility", facility);
        put(values, "building", building);
        put(values, "system", system);
        put(values, "pointOfUse", pointOfUse);
        put(values, "basis", basis);
        return LocationDashboardIdentitySupport.immutableCopy(values);
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
