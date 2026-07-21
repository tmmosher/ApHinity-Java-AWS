package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record LocationDashboardImportStrategyConfig(
    String locationName,
    List<SublocationConfig> sublocations,
    List<SystemTypeConfig> systems,
    List<GraphConfig> graphs,
    List<DerivedGraphConfig> derivedGraphs,
    List<SystemTypeAliasConfig> systemTypeAliases,
    List<SpreadsheetIdentityColumn> identityPattern,
    List<MeasurementUnitConfig> measurementUnits,
    List<RangeProfileConfig> rangeProfiles
) {
    public LocationDashboardImportStrategyConfig {
        identityPattern = identityPattern == null ? List.of() : List.copyOf(identityPattern);
        measurementUnits = measurementUnits == null ? List.of() : List.copyOf(measurementUnits);
        rangeProfiles = rangeProfiles == null ? List.of() : List.copyOf(rangeProfiles);
    }

    public LocationDashboardImportStrategyConfig(
        String locationName,
        List<SublocationConfig> sublocations,
        List<SystemTypeConfig> systems,
        List<GraphConfig> graphs,
        List<DerivedGraphConfig> derivedGraphs,
        List<SystemTypeAliasConfig> systemTypeAliases,
        List<SpreadsheetIdentityColumn> identityPattern,
        List<MeasurementUnitConfig> measurementUnits
    ) {
        this(locationName, sublocations, systems, graphs, derivedGraphs, systemTypeAliases,
            identityPattern, measurementUnits, List.of());
    }

    public LocationDashboardImportStrategyConfig(
        String locationName,
        List<SublocationConfig> sublocations,
        List<SystemTypeConfig> systems,
        List<GraphConfig> graphs,
        List<DerivedGraphConfig> derivedGraphs,
        List<SystemTypeAliasConfig> systemTypeAliases,
        List<SpreadsheetIdentityColumn> identityPattern
    ) {
        this(locationName, sublocations, systems, graphs, derivedGraphs, systemTypeAliases,
            identityPattern, List.of(), List.of());
    }

    public LocationDashboardImportStrategyConfig(
        String locationName,
        List<SublocationConfig> sublocations,
        List<SystemTypeConfig> systems,
        List<GraphConfig> graphs,
        List<DerivedGraphConfig> derivedGraphs,
        List<SystemTypeAliasConfig> systemTypeAliases
    ) {
        this(locationName, sublocations, systems, graphs, derivedGraphs, systemTypeAliases,
            List.of(), List.of(), List.of());
    }

    public record SpreadsheetIdentityColumn(
        String column,
        List<String> aliases
    ) {
        public SpreadsheetIdentityColumn {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        /**
         * The configured spreadsheet column is also the identity key. Identity
         * semantics are deliberately not inferred by the generic importer.
         */
        public String identityKey() {
            return column;
        }
    }

    public record MeasurementUnitConfig(
        String value,
        List<String> aliases,
        @JsonProperty("for_measurement_names")
        List<String> forMeasurementNames
    ) {
        public MeasurementUnitConfig {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            forMeasurementNames = forMeasurementNames == null ? List.of() : List.copyOf(forMeasurementNames);
        }

        public MeasurementUnitConfig(String value, List<String> aliases) {
            this(value, aliases, List.of());
        }
    }

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

    public record RangeProfileConfig(
        String key,
        List<String> measurementTypes
    ) {
        public RangeProfileConfig {
            measurementTypes = measurementTypes == null ? List.of() : List.copyOf(measurementTypes);
        }
    }

    public record SystemTypeAliasConfig(
        String canonicalName,
        List<String> aliases
    ) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static SystemTypeAliasConfig fromValue(Map<String, List<String>> rawValue) {
            if (rawValue == null || rawValue.isEmpty()) {
                return null;
            }
            if (rawValue.size() != 1) {
                throw new IllegalArgumentException("Dashboard system type aliases must define exactly one canonical name");
            }
            Map.Entry<String, List<String>> entry = rawValue.entrySet().iterator().next();
            return new SystemTypeAliasConfig(entry.getKey(), entry.getValue());
        }
    }

    public record GraphConfig(
        String id,
        String name,
        String title,
        ImportType importType,
        String sublocationKey,
        List<String> traceOrder,
        Map<String, String> traceColors,
        String graphType,
        GraphAnchor anchor,
        GraphDimension traceBy
    ) {
        public GraphConfig(
            String id,
            String name,
            String title,
            ImportType importType,
            String sublocationKey,
            List<String> traceOrder,
            Map<String, String> traceColors,
            String graphType
        ) {
            this(id, name, title, importType, sublocationKey, traceOrder, traceColors, graphType, null, null);
        }

        public GraphAnchor effectiveAnchor() {
            if (anchor != null) {
                return anchor;
            }
            // Preserve existing location configurations while new graphs use
            // the dimension-aware anchor object.
            if (sublocationKey == null || sublocationKey.isBlank()) {
                return null;
            }
            return new GraphAnchor(GraphDimension.SUBLOCATION, sublocationKey);
        }

        public GraphDimension effectiveTraceBy() {
            if (traceBy != null) {
                return traceBy;
            }
            return importType == ImportType.SYSTEM_TYPE_COMPLIANCE
                ? GraphDimension.SYSTEM
                : GraphDimension.MEASUREMENT;
        }
    }

    public record GraphAnchor(
        GraphDimension dimension,
        String key
    ) {
    }

    public enum GraphDimension {
        SUBLOCATION("sublocation"),
        SYSTEM("system"),
        MEASUREMENT("measurement");

        private final String value;

        GraphDimension(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static GraphDimension fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                .filter(dimension ->
                    dimension.value.equals(normalized)
                        || dimension.name().equalsIgnoreCase(normalized)
                )
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard graph dimension: " + rawValue));
        }
    }

    public record DerivedGraphConfig(
        String id,
        String name,
        String title,
        DerivedGraphType derivedType,
        String graphType,
        List<DerivedGraphHierarchyLevel> hierarchy
    ) {
        public DerivedGraphConfig {
            hierarchy = hierarchy == null ? List.of() : List.copyOf(hierarchy);
        }

        public DerivedGraphConfig(
            String id,
            String name,
            String title,
            DerivedGraphType derivedType,
            String graphType
        ) {
            this(id, name, title, derivedType, graphType, List.of());
        }
    }

    public record DerivedGraphHierarchyLevel(
        DerivedGraphHierarchySource source,
        String key
    ) {
    }

    public enum DerivedGraphHierarchySource {
        IDENTITY("identity"),
        MEASUREMENT("measurement");

        private final String value;

        DerivedGraphHierarchySource(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static DerivedGraphHierarchySource fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                .filter(source -> source.value.equals(normalized) || source.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown derived graph hierarchy source: " + rawValue));
        }
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

    public record RangeProfile(String value) {

        public RangeProfile {
            value = value == null ? null : value.strip().toLowerCase(Locale.ROOT);
        }

        @JsonCreator
        public static RangeProfile fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            return new RangeProfile(rawValue);
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum DerivedGraphType {
        TOTAL_SAMPLES("total_samples"),
        TOTAL_NON_CONFORMANCES("total_non_conformances"),
        ACTIVE_NON_CONFORMANCE_PERCENT("active_non_conformance_percent"),
        PERCENT_CONFORMANCE("percent_conformance"),
        NON_CONFORMANCE_COUNT("non_conformance_count"),
        PERCENT_RESOLVED("percent_resolved"),
        NON_CONFORMANCES_BY_FACILITY("non_conformances_by_facility"),
        NON_CONFORMANCES_BY_SYSTEM_TYPE("non_conformances_by_system_type"),
        NON_CONFORMANCES_BY_CATEGORY("non_conformances_by_category"),
        NON_CONFORMANCE_STATUS_BY_FACILITY("non_conformance_status_by_facility"),
        NON_CONFORMANCE_TURNAROUND_TIME("non_conformance_turnaround_time"),
        RECENT_SAMPLE_MEASUREMENTS("recent_sample_measurements"),
        SAMPLE_CONFORMANCE_HIERARCHY("sample_conformance_hierarchy");

        private final String value;

        DerivedGraphType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        public boolean requiresResolvedNonConformanceState() {
            return switch (this) {
                case ACTIVE_NON_CONFORMANCE_PERCENT,
                     PERCENT_RESOLVED,
                     NON_CONFORMANCE_STATUS_BY_FACILITY,
                     NON_CONFORMANCE_TURNAROUND_TIME -> true;
                default -> false;
            };
        }

        @JsonCreator
        public static DerivedGraphType fromValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                .filter(derivedGraphType ->
                    derivedGraphType.value.equals(normalized)
                        || derivedGraphType.name().equalsIgnoreCase(normalized)
                )
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dashboard derived graph type: " + rawValue));
        }
    }
}
