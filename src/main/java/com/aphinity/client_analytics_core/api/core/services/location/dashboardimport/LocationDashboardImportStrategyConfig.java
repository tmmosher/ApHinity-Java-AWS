package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record LocationDashboardImportStrategyConfig(
    String locationName,
    List<SublocationConfig> sublocations,
    List<SystemTypeConfig> systems,
    List<GraphConfig> graphs
) {
    public record SublocationConfig(
        String key,
        String displayName,
        List<String> facilityAliases,
        List<String> buildingAliases,
        boolean defaultForFacility
    ) {
    }

    public record SystemTypeConfig(
        String key,
        String displayName,
        RangeProfile rangeProfile,
        List<String> aliases
    ) {
    }

    public record GraphConfig(
        String id,
        String title,
        ImportType importType,
        String sublocationKey,
        List<String> traceOrder,
        Map<String, String> traceColors,
        String graphType
    ) {
    }

    public enum ImportType {
        SYSTEM_TYPE_COMPLIANCE("system_type_compliance"),
        WATER_QUALITY_COMPLIANCE("water_quality_compliance");

        private final String value;

        ImportType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static ImportType fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                .filter(importType ->
                    importType.value.equals(normalized)
                        || importType.name().equalsIgnoreCase(normalized)
                )
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard import type: " + rawValue));
        }
    }

    public enum RangeProfile {
        CRITICAL("critical"),
        UTILITY("utility"),
        POTABLE("potable"),
        TOWERS("towers");

        private final String value;

        RangeProfile(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static RangeProfile fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                .filter(rangeProfile ->
                    rangeProfile.value.equals(normalized)
                        || rangeProfile.name().equalsIgnoreCase(normalized)
                )
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown measurement range profile: " + rawValue));
        }

        public boolean isCompliant(BigDecimal numericValue, MeasurementBound measurementBound) {
            if (numericValue == null || measurementBound == null) {
                return false;
            }
            BigDecimal min = switch (this) {
                case CRITICAL -> measurementBound.getCriticalRangeMin();
                case UTILITY -> measurementBound.getUtilityRangeMin();
                case POTABLE -> measurementBound.getPotableRangeMin();
                case TOWERS -> measurementBound.getTowersRangeMin();
            };
            BigDecimal max = switch (this) {
                case CRITICAL -> measurementBound.getCriticalRangeMax();
                case UTILITY -> measurementBound.getUtilityRangeMax();
                case POTABLE -> measurementBound.getPotableRangeMax();
                case TOWERS -> measurementBound.getTowersRangeMax();
            };
            if (min == null && max == null) {
                return false;
            }
            if (min != null && numericValue.compareTo(min) < 0) {
                return false;
            }
            if (max != null && numericValue.compareTo(max) > 0) {
                return false;
            }
            return true;
        }
    }
}
