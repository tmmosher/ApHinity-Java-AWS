package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardSampleImportPipeline.SampleImportResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeAliasConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

/**
 * Generic location import strategy backed by a JSON configuration file.
 * The strategy keeps location-specific grouping rules in configuration while the import
 * behavior stays centralized and testable.
 */
public class ConfiguredLocationDashboardImportStrategy implements LocationDashboardImportStrategy {
    private final LocationDashboardImportStrategyConfig config;
    private final LocationDashboardImportContextResolver contextResolver;
    private final LocationDashboardSampleImportPipeline sampleImportPipeline;
    private final LocationDashboardObservationAggregator observationAggregator;
    private final LocationDashboardCorrectiveActionDraftFactory correctiveActionDraftFactory;

    public ConfiguredLocationDashboardImportStrategy(LocationDashboardImportStrategyConfig config) {
        this.config = validate(config);
        Map<String, SystemTypeConfig> systemsByAlias = buildSystemsByAlias(this.config.systems());
        Map<String, SystemTypeAliasGroup> systemTypeAliasGroupsByAlias = buildSystemTypeAliasGroupsByAlias(
            this.config.systemTypeAliases(),
            systemsByAlias
        );
        Map<String, List<SublocationConfig>> sublocationsByFacilityAlias =
            buildSublocationsByFacilityAlias(this.config.sublocations());
        Map<String, SublocationConfig> defaultSublocationsByFacilityAlias =
            buildDefaultSublocationsByFacilityAlias(this.config.sublocations());
        Map<String, List<GraphConfig>> graphsBySublocationKey = buildGraphsBySublocationKey(this.config.graphs());
        this.contextResolver = new LocationDashboardImportContextResolver(
            this.config,
            systemsByAlias,
            adaptAliasGroups(systemTypeAliasGroupsByAlias),
            sublocationsByFacilityAlias,
            defaultSublocationsByFacilityAlias
        );
        this.sampleImportPipeline = new LocationDashboardSampleImportPipeline(
            this.contextResolver,
            new LocationDashboardCommentParser(this.config.measurementUnits())
        );
        this.observationAggregator = new LocationDashboardObservationAggregator(graphsBySublocationKey, this.config.graphs());
        this.correctiveActionDraftFactory = new LocationDashboardCorrectiveActionDraftFactory();
    }

    @Override
    public String locationName() {
        return config.locationName();
    }

    @Override
    public List<GraphConfig> graphDefinitions() {
        return config.graphs();
    }

    @Override
    public List<DerivedGraphConfig> derivedGraphDefinitions() {
        return config.derivedGraphs() == null ? List.of() : config.derivedGraphs();
    }

    @Override
    public List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> spreadsheetIdentityPattern() {
        return config.identityPattern();
    }

    @Override
    public LocationDashboardImportComputation computeImport(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        List<MeasurementBound> measurementBounds
    ) {
        Map<String, MeasurementBound> measurementBoundsByName = new LinkedHashMap<>();
        for (MeasurementBound measurementBound : measurementBounds) {
            if (measurementBound.getMeasurementName() == null) {
                continue;
            }
            measurementBoundsByName.put(normalizeKey(measurementBound.getMeasurementName()), measurementBound);
        }

        SampleImportResult sampleImportResult =
            sampleImportPipeline.importSamples(workbook, measurementBoundsByName);
        List<LocationDashboardAnalyzedSample> analyzedSamples = sampleImportResult.sampleBuckets().analyzedSamples();
        LocationDashboardObservationAggregator.ObservationAggregationResult aggregationResult =
            observationAggregator.aggregate(analyzedSamples);

        return new LocationDashboardImportComputation(
            aggregationResult.graphs(),
            aggregationResult.observations(),
            correctiveActionDraftFactory.buildCorrectiveActions(analyzedSamples),
            aggregationResult.analyzedSamples()
        );
    }

    private LocationDashboardImportStrategyConfig validate(LocationDashboardImportStrategyConfig rawConfig) {
        if (rawConfig == null || rawConfig.locationName() == null || rawConfig.locationName().isBlank()) {
            throw new IllegalStateException("Dashboard import strategy location name is required");
        }
        if (rawConfig.sublocations() == null || rawConfig.sublocations().isEmpty()) {
            throw new IllegalStateException("Dashboard import strategy sublocations are required");
        }
        if (rawConfig.systems() == null || rawConfig.systems().isEmpty()) {
            throw new IllegalStateException("Dashboard import strategy systems are required");
        }
        if (rawConfig.graphs() == null || rawConfig.graphs().isEmpty()) {
            throw new IllegalStateException("Dashboard import strategy graphs are required");
        }

        Set<String> sublocationKeys = new LinkedHashSet<>();
        Set<String> defaultFacilityAliases = new LinkedHashSet<>();
        for (SublocationConfig sublocation : rawConfig.sublocations()) {
            String normalizedKey = normalizeKey(sublocation.key());
            if (normalizedKey == null || !sublocationKeys.add(normalizedKey)) {
                throw new IllegalStateException("Dashboard import strategy sublocation keys must be unique");
            }
            if (sublocation.defaultForFacility()) {
                for (String facilityAlias : effectiveFacilityAliases(sublocation)) {
                    String normalizedFacilityAlias = normalizeKey(facilityAlias);
                    if (normalizedFacilityAlias == null) {
                        continue;
                    }
                    if (!defaultFacilityAliases.add(normalizedFacilityAlias)) {
                        throw new IllegalStateException(
                            "Dashboard import strategy default sublocations must be unique per facility alias"
                        );
                    }
                }
            }
        }

        Set<String> systemKeys = new LinkedHashSet<>();
        Map<String, String> systemOwnersByAlias = new LinkedHashMap<>();
        for (SystemTypeConfig system : rawConfig.systems()) {
            String normalizedSystemKey = normalizeKey(system.key());
            if (normalizedSystemKey == null) {
                throw new IllegalStateException("Dashboard import strategy system keys are required");
            }
            if (!systemKeys.add(normalizedSystemKey)) {
                throw new IllegalStateException("Dashboard import strategy system keys must be unique");
            }
            if (system.rangeProfile() == null) {
                throw new IllegalStateException(
                    "Dashboard import strategy systems must declare a range profile: " + system.key()
                );
            }
            validateAliasOwnership(
                systemOwnersByAlias,
                normalizedSystemKey,
                normalizedSystemKey,
                "Dashboard import system aliases must be unique: " + system.key()
            );
            if (system.aliases() != null) {
                for (String alias : system.aliases()) {
                    validateAliasOwnership(
                        systemOwnersByAlias,
                        normalizeKey(alias),
                        normalizedSystemKey,
                        "Dashboard import system aliases must be unique: " + alias
                    );
                }
            }
        }

        Map<String, String> systemTypeAliases = new LinkedHashMap<>();
        if (rawConfig.systemTypeAliases() != null) {
            for (SystemTypeAliasConfig systemTypeAlias : rawConfig.systemTypeAliases()) {
                String normalizedCanonicalName = normalizeKey(systemTypeAlias.canonicalName());
                if (normalizedCanonicalName == null) {
                    throw new IllegalStateException("Dashboard import strategy system type alias names are required");
                }
                if (!systemKeys.contains(normalizedCanonicalName)) {
                    throw new IllegalStateException(
                        "Dashboard import system type aliases must reference a configured canonical system: "
                            + systemTypeAlias.canonicalName()
                    );
                }
                if (systemTypeAlias.aliases() == null || systemTypeAlias.aliases().isEmpty()) {
                    throw new IllegalStateException(
                        "Dashboard import strategy system type aliases must define at least one alias: "
                            + systemTypeAlias.canonicalName()
                    );
                }
                for (String alias : systemTypeAlias.aliases()) {
                    validateAliasOwnership(
                        systemTypeAliases,
                        normalizeKey(alias),
                        normalizedCanonicalName,
                        "Dashboard import system type aliases must be unique: " + alias
                    );
                }
            }
        }

        Set<String> graphIds = new LinkedHashSet<>();
        Set<String> graphNameTitles = new LinkedHashSet<>();
        for (GraphConfig graph : rawConfig.graphs()) {
            String normalizedGraphId = normalizeKey(graph.id());
            if (normalizedGraphId == null || !graphIds.add(normalizedGraphId)) {
                throw new IllegalStateException("Dashboard import strategy graph ids must be unique");
            }
            String normalizedGraphName = normalizeKey(graph.name());
            if (normalizedGraphName == null) {
                throw new IllegalStateException("Dashboard import graphs must declare a graph name: " + graph.id());
            }
            String normalizedGraphTitle = normalizeKey(graph.title());
            if (normalizedGraphTitle == null) {
                throw new IllegalStateException("Dashboard import graphs must declare a graph title: " + graph.id());
            }
            String graphNameTitle = normalizedGraphName + "|" + normalizedGraphTitle;
            if (!graphNameTitles.add(graphNameTitle)) {
                throw new IllegalStateException("Dashboard import strategy graph name/title combinations must be unique");
            }
            if (graph.importType() == null) {
                throw new IllegalStateException("Dashboard import graphs must declare an import type: " + graph.id());
            }
            if (!sublocationKeys.contains(normalizeKey(graph.sublocationKey()))) {
                throw new IllegalStateException("Dashboard import graph references an unknown sublocation key: " + graph.sublocationKey());
            }
        }

        Set<String> derivedGraphIds = new LinkedHashSet<>();
        Set<String> derivedNameTitles = new LinkedHashSet<>();
        if (rawConfig.derivedGraphs() != null) {
            for (DerivedGraphConfig derivedGraph : rawConfig.derivedGraphs()) {
                String normalizedDerivedGraphId = normalizeKey(derivedGraph.id());
                if (normalizedDerivedGraphId == null || !derivedGraphIds.add(normalizedDerivedGraphId)) {
                    throw new IllegalStateException("Dashboard import strategy derived graph ids must be unique");
                }
                String normalizedDerivedGraphName = normalizeKey(derivedGraph.name());
                if (normalizedDerivedGraphName == null) {
                    throw new IllegalStateException(
                        "Dashboard import derived graphs must declare a graph name: " + derivedGraph.id()
                    );
                }
                String normalizedDerivedGraphTitle = normalizeKey(derivedGraph.title());
                String derivedNameTitle = normalizedDerivedGraphName + "|" + nullSafe(normalizedDerivedGraphTitle);
                if (!derivedNameTitles.add(derivedNameTitle)) {
                    throw new IllegalStateException(
                        "Dashboard import strategy derived graph name/title combinations must be unique"
                    );
                }
                if (derivedGraph.derivedType() == null) {
                    throw new IllegalStateException(
                        "Dashboard import derived graphs must declare a derived type: " + derivedGraph.id()
                    );
                }
                if (graphNameTitles.contains(derivedNameTitle)) {
                    throw new IllegalStateException(
                        "Dashboard import strategy graph name/title combinations must be unique across imported and derived graphs"
                    );
                }
            }
        }

        return rawConfig;
    }

    private Map<String, SystemTypeConfig> buildSystemsByAlias(List<SystemTypeConfig> systems) {
        Map<String, SystemTypeConfig> systemsByAlias = new LinkedHashMap<>();
        for (SystemTypeConfig systemType : systems) {
            registerAlias(systemsByAlias, systemType.key(), systemType);
            if (systemType.aliases() != null) {
                for (String alias : systemType.aliases()) {
                    registerAlias(systemsByAlias, alias, systemType);
                }
            }
        }
        return Map.copyOf(systemsByAlias);
    }

    private Map<String, SystemTypeAliasGroup> buildSystemTypeAliasGroupsByAlias(
        List<SystemTypeAliasConfig> configuredAliases,
        Map<String, SystemTypeConfig> configuredSystemsByAlias
    ) {
        if (configuredAliases == null || configuredAliases.isEmpty()) {
            return Map.of();
        }

        Map<String, SystemTypeAliasGroup> aliasGroupsByAlias = new LinkedHashMap<>();
        for (SystemTypeAliasConfig configuredAlias : configuredAliases) {
            SystemTypeAliasGroup aliasGroup = new SystemTypeAliasGroup(
                resolveCanonicalSystemType(configuredAlias, configuredSystemsByAlias)
            );
            for (String alias : configuredAlias.aliases()) {
                String normalizedAlias = normalizeKey(alias);
                if (normalizedAlias == null) {
                    continue;
                }
                SystemTypeAliasGroup existingAliasGroup = aliasGroupsByAlias.putIfAbsent(normalizedAlias, aliasGroup);
                if (existingAliasGroup != null
                    && !sameSystemType(existingAliasGroup.representativeSystemType(), aliasGroup.representativeSystemType())) {
                    throw new IllegalStateException("Dashboard import system type aliases must be unique: " + alias);
                }
            }
        }
        return Map.copyOf(aliasGroupsByAlias);
    }

    private SystemTypeConfig resolveCanonicalSystemType(
        SystemTypeAliasConfig configuredAlias,
        Map<String, SystemTypeConfig> configuredSystemsByAlias
    ) {
        SystemTypeConfig canonicalSystemType = configuredSystemsByAlias.get(normalizeKey(configuredAlias.canonicalName()));
        if (canonicalSystemType == null) {
            throw new IllegalStateException(
                "Dashboard import system type aliases must reference a configured canonical system: "
                    + configuredAlias.canonicalName()
            );
        }
        return canonicalSystemType;
    }

    private Map<String, List<SublocationConfig>> buildSublocationsByFacilityAlias(List<SublocationConfig> sublocations) {
        Map<String, List<SublocationConfig>> sublocationsByFacilityAlias = new LinkedHashMap<>();
        for (SublocationConfig sublocation : sublocations) {
            List<String> aliases = effectiveFacilityAliases(sublocation);
            for (String alias : aliases) {
                String normalizedAlias = normalizeKey(alias);
                if (normalizedAlias == null) {
                    continue;
                }
                sublocationsByFacilityAlias.computeIfAbsent(normalizedAlias, ignored -> new ArrayList<>()).add(sublocation);
            }
        }
        return Map.copyOf(sublocationsByFacilityAlias);
    }

    private Map<String, SublocationConfig> buildDefaultSublocationsByFacilityAlias(List<SublocationConfig> sublocations) {
        Map<String, SublocationConfig> defaultsByFacilityAlias = new LinkedHashMap<>();
        for (SublocationConfig sublocation : sublocations) {
            if (!sublocation.defaultForFacility()) {
                continue;
            }
            List<String> aliases = effectiveFacilityAliases(sublocation);
            for (String alias : aliases) {
                String normalizedAlias = normalizeKey(alias);
                if (normalizedAlias == null) {
                    continue;
                }
                defaultsByFacilityAlias.put(normalizedAlias, sublocation);
            }
        }
        return Map.copyOf(defaultsByFacilityAlias);
    }

    private Map<String, List<GraphConfig>> buildGraphsBySublocationKey(List<GraphConfig> graphDefinitions) {
        Map<String, List<GraphConfig>> graphsBySublocationKey = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            graphsBySublocationKey.computeIfAbsent(normalizeKey(graphDefinition.sublocationKey()), ignored -> new ArrayList<>())
                .add(graphDefinition);
        }
        return Map.copyOf(graphsBySublocationKey);
    }

    private void registerAlias(Map<String, SystemTypeConfig> systemsByAlias, String rawAlias, SystemTypeConfig systemType) {
        String normalizedAlias = normalizeKey(rawAlias);
        if (normalizedAlias == null) {
            return;
        }
        SystemTypeConfig existingSystemType = systemsByAlias.putIfAbsent(normalizedAlias, systemType);
        if (existingSystemType != null && !sameSystemType(existingSystemType, systemType)) {
            throw new IllegalStateException("Dashboard import system aliases must be unique: " + rawAlias);
        }
    }

    private void validateAliasOwnership(
        Map<String, String> ownersByAlias,
        String normalizedAlias,
        String normalizedOwner,
        String errorMessage
    ) {
        if (normalizedAlias == null || normalizedOwner == null) {
            return;
        }
        String existingOwner = ownersByAlias.putIfAbsent(normalizedAlias, normalizedOwner);
        if (existingOwner != null && !Objects.equals(existingOwner, normalizedOwner)) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private boolean sameSystemType(SystemTypeConfig left, SystemTypeConfig right) {
        return Objects.equals(normalizeKey(left == null ? null : left.key()), normalizeKey(right == null ? null : right.key()));
    }

    private static String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }

    private List<String> effectiveFacilityAliases(SublocationConfig sublocation) {
        if (sublocation.facilityAliases() == null || sublocation.facilityAliases().isEmpty()) {
            List<String> aliases = new ArrayList<>();
            if (sublocation.displayName() != null) {
                aliases.add(sublocation.displayName());
            }
            if (sublocation.key() != null) {
                aliases.add(sublocation.key());
            }
            return List.copyOf(aliases);
        }
        return sublocation.facilityAliases();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    String canonicalFacilityName(String rawFacility, String rawBuilding) {
        return contextResolver.canonicalFacilityName(rawFacility, rawBuilding);
    }

    String canonicalSystemTypeName(String rawSystem) {
        return contextResolver.canonicalSystemTypeName(rawSystem);
    }

    private Map<String, LocationDashboardImportContextResolver.SystemTypeAliasGroup> adaptAliasGroups(
        Map<String, SystemTypeAliasGroup> aliasGroupsByAlias
    ) {
        Map<String, LocationDashboardImportContextResolver.SystemTypeAliasGroup> adapted = new LinkedHashMap<>();
        aliasGroupsByAlias.forEach((alias, group) -> adapted.put(
            alias,
            new LocationDashboardImportContextResolver.SystemTypeAliasGroup(group.representativeSystemType())
        ));
        return Map.copyOf(adapted);
    }

    private record SystemTypeAliasGroup(
        SystemTypeConfig representativeSystemType
    ) {
    }
}
