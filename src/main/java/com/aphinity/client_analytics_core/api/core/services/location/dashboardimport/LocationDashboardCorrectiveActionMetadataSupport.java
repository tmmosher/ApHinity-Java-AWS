package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps corrective-action structured metadata distinct from user-entered comment text.
 * Reserved "Import ..." labels are parsed first so free-form comment lines cannot override
 * the persisted measurement/facility/system metadata used for derived graph reconstruction.
 */
final class LocationDashboardCorrectiveActionMetadataSupport {
    private static final String IMPORT_MEASUREMENT_LABEL = "Import Measurement";
    private static final String IMPORT_OBSERVED_AT_LABEL = "Import Observed At";
    private static final String IMPORT_SUBLOCATION_LABEL = "Import Sublocation";
    private static final String IMPORT_FACILITY_LABEL = "Import Facility";
    private static final String IMPORT_BUILDING_LABEL = "Import Building";
    private static final String IMPORT_SYSTEM_LABEL = "Import System";
    private static final String IMPORT_POINT_OF_USE_LABEL = "Import Point of Use";
    private static final String IMPORT_BASIS_LABEL = "Import Basis";
    private static final String IMPORT_SAMPLE_IDENTITY_LABEL = "Import Sample Identity";

    private LocationDashboardCorrectiveActionMetadataSupport() {
    }

    static String measurementLine(String measurementName) {
        return metadataLine(IMPORT_MEASUREMENT_LABEL, measurementName);
    }

    static String observedAtLine(LocalDate observedDate) {
        return metadataLine(IMPORT_OBSERVED_AT_LABEL, observedDate == null ? null : observedDate.toString());
    }

    static String sublocationLine(String sublocationName) {
        return metadataLine(IMPORT_SUBLOCATION_LABEL, sublocationName);
    }

    static String facilityLine(String facilityName) {
        return metadataLine(IMPORT_FACILITY_LABEL, facilityName);
    }

    static String buildingLine(String buildingName) {
        return metadataLine(IMPORT_BUILDING_LABEL, buildingName);
    }

    static String systemLine(String systemName) {
        return metadataLine(IMPORT_SYSTEM_LABEL, systemName);
    }

    static String pointOfUseLine(String pointOfUse) {
        return metadataLine(IMPORT_POINT_OF_USE_LABEL, pointOfUse);
    }

    static String basisLine(String basis) {
        return metadataLine(IMPORT_BASIS_LABEL, basis);
    }

    static String sampleIdentityLine(String sampleIdentity) {
        return metadataLine(IMPORT_SAMPLE_IDENTITY_LABEL, sampleIdentity);
    }

    static Map<String, String> parseStructuredMetadata(String description) {
        if (description == null || description.isBlank()) {
            return Map.of();
        }

        Map<String, String> valuesByField = new LinkedHashMap<>();
        for (String line : description.split("\\R")) {
            if (line == null) {
                continue;
            }
            int delimiterIndex = line.indexOf(':');
            if (delimiterIndex < 0) {
                continue;
            }
            String label = LocationDashboardGraphMetadataSupport.normalizeKey(line.substring(0, delimiterIndex));
            String value = line.substring(delimiterIndex + 1).strip();
            if (label == null || value.isBlank()) {
                continue;
            }

            String reservedField = reservedField(label);
            if (reservedField != null) {
                valuesByField.put(reservedField, value);
                continue;
            }

            String legacyField = legacyField(label);
            if (legacyField != null) {
                valuesByField.putIfAbsent(legacyField, value);
            }
        }
        return Map.copyOf(valuesByField);
    }

    static String identityKey(String title, String description) {
        Map<String, String> metadata = parseStructuredMetadata(description);
        String identity = identityKey(
            metadata.get("measurement"),
            LocationDashboardGraphMetadataSupport.parseLocalDate(metadata.get("observed at")),
            LocationDashboardGraphMetadataSupport.firstNonBlank(
                metadata.get("sublocation"),
                metadata.get("facility")
            ),
            metadata.get("building"),
            metadata.get("system"),
            metadata.get("point of use"),
            metadata.get("basis"),
            metadata.get("sample identity")
        );
        return identity != null ? identity : LocationDashboardGraphMetadataSupport.normalizeKey(title);
    }

    static String identityKey(
        String measurementName,
        LocalDate observedAt,
        String facilityName,
        String buildingName,
        String systemName,
        String pointOfUse,
        String basis,
        String sampleIdentity
    ) {
        String measurement = LocationDashboardGraphMetadataSupport.normalizeKey(measurementName);
        String observedAtValue = observedAt == null ? null : observedAt.toString();
        String observedAtNormalized = LocationDashboardGraphMetadataSupport.normalizeKey(observedAtValue);
        String facility = LocationDashboardGraphMetadataSupport.normalizeKey(facilityName);
        String building = nullSafeNormalized(buildingName);
        String system = LocationDashboardGraphMetadataSupport.normalizeKey(systemName);
        if (measurement != null && observedAtNormalized != null && facility != null && system != null) {
            return String.join("|", List.of(
                measurement,
                observedAtNormalized,
                facility,
                building,
                system,
                nullSafeNormalized(pointOfUse),
                nullSafeNormalized(basis),
                nullSafeNormalized(sampleIdentity)
            ));
        }
        return null;
    }

    private static String metadataLine(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value;
    }

    private static String reservedField(String normalizedLabel) {
        return switch (normalizedLabel) {
            case "import measurement" -> "measurement";
            case "import observed at" -> "observed at";
            case "import sublocation" -> "sublocation";
            case "import facility" -> "facility";
            case "import building" -> "building";
            case "import system" -> "system";
            case "import point of use" -> "point of use";
            case "import basis" -> "basis";
            case "import sample identity" -> "sample identity";
            default -> null;
        };
    }

    private static String legacyField(String normalizedLabel) {
        return switch (normalizedLabel) {
            case "measurement" -> "measurement";
            case "observed at" -> "observed at";
            case "sublocation" -> "sublocation";
            case "facility" -> "facility";
            case "building" -> "building";
            case "system" -> "system";
            case "point of use" -> "point of use";
            case "basis" -> "basis";
            default -> null;
        };
    }

    private static String nullSafeNormalized(String value) {
        String normalized = LocationDashboardGraphMetadataSupport.normalizeKey(value);
        return normalized == null ? "" : normalized;
    }
}
