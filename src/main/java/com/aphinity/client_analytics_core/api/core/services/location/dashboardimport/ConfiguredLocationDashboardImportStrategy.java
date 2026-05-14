package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeAliasConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

/**
 * Generic location import strategy backed by a JSON configuration file.
 * The strategy keeps location-specific grouping rules in configuration while the import
 * behavior stays centralized and testable.
 */
public class ConfiguredLocationDashboardImportStrategy implements LocationDashboardImportStrategy {
    // Temporary testing switch: disable all corrective-action creation from cell comments.
    // To restore comment-derived incidents, re-enable this and revisit:
    // - strict structured comment validation in LocationDashboardSampleImportPipeline.validatePrimaryCommentSample(...)
    // - the dormant comment corrective-action helpers in this strategy
    // - the dedicated drafting policy in LocationDashboardCorrectiveActionDraftFactory
    private static final boolean ENABLE_COMMENT_DERIVED_CORRECTIVE_ACTIONS = false;

    private final LocationDashboardImportStrategyConfig config;
    private final Map<String, SystemTypeConfig> systemsByAlias;
    private final Map<String, SystemTypeAliasGroup> systemTypeAliasGroupsByAlias;
    private final Map<String, List<SublocationConfig>> sublocationsByFacilityAlias;
    private final Map<String, SublocationConfig> defaultSublocationsByFacilityAlias;
    private final Map<String, List<GraphConfig>> graphsBySublocationKey;
    private final LocationDashboardCommentParser commentParser;
    private final LocationDashboardImportContextResolver contextResolver;
    private final LocationDashboardSampleImportPipeline sampleImportPipeline;
    private final LocationDashboardObservationAggregator observationAggregator;
    private final LocationDashboardCorrectiveActionDraftFactory correctiveActionDraftFactory;

    public ConfiguredLocationDashboardImportStrategy(LocationDashboardImportStrategyConfig config) {
        this.config = validate(config);
        this.systemsByAlias = buildSystemsByAlias(this.config.systems());
        this.systemTypeAliasGroupsByAlias = buildSystemTypeAliasGroupsByAlias(
            this.config.systemTypeAliases(),
            this.systemsByAlias
        );
        this.sublocationsByFacilityAlias = buildSublocationsByFacilityAlias(this.config.sublocations());
        this.defaultSublocationsByFacilityAlias = buildDefaultSublocationsByFacilityAlias(this.config.sublocations());
        this.graphsBySublocationKey = buildGraphsBySublocationKey(this.config.graphs());
        this.commentParser = new LocationDashboardCommentParser();
        this.contextResolver = new LocationDashboardImportContextResolver(
            this.config,
            this.systemsByAlias,
            adaptAliasGroups(this.systemTypeAliasGroupsByAlias),
            this.sublocationsByFacilityAlias,
            this.defaultSublocationsByFacilityAlias
        );
        this.sampleImportPipeline = new LocationDashboardSampleImportPipeline(
            this.contextResolver,
            this.commentParser,
            ENABLE_COMMENT_DERIVED_CORRECTIVE_ACTIONS
        );
        this.observationAggregator = new LocationDashboardObservationAggregator(this.graphsBySublocationKey, this.config.graphs());
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

        LocationDashboardSampleImportPipeline.SampleImportResult sampleImportResult =
            sampleImportPipeline.importSamples(workbook, measurementBoundsByName);
        List<LocationDashboardAnalyzedSample> analyzedSamples = sampleImportResult.sampleBuckets().analyzedSamples();
        LocationDashboardObservationAggregator.ObservationAggregationResult aggregationResult =
            observationAggregator.aggregate(analyzedSamples);

        return new LocationDashboardImportComputation(
            aggregationResult.graphs(),
            aggregationResult.observations(),
            correctiveActionDraftFactory.buildWorksheetCorrectiveActions(analyzedSamples),
            aggregationResult.analyzedSamples()
        );
    }

    private LocationDashboardWorksheetSample buildWorksheetSample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String facilityName,
        String resolvedBuilding,
        String resolvedSystem,
        MeasurementBound measurementBound
    ) {
        if (cell == null
            || cell.numericValue() == null
            || systemType == null
            || measurementBound == null
            || facilityName == null) {
            return null;
        }
        return new LocationDashboardWorksheetSample(
            cell.observedDate(),
            cell.numericValue(),
            resolveMeasurementName(cell.metricName(), measurementBound),
            facilityName,
            sublocation,
            systemType,
            measurementBound,
            resolvedBuilding,
            resolvedSystem,
            row == null ? null : row.pointOfUse(),
            row == null ? null : row.basis(),
            cell.cellReference()
        );
    }

    private List<LocationDashboardImportedSample> extractCommentSamples(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        MeasurementBound measurementBound,
        SystemTypeConfig systemType,
        String facilityName,
        SublocationConfig sublocation,
        String resolvedBuilding,
        String resolvedSystem
    ) {
        if (parsedComment == null
            || primaryCell == null
            || measurementBound == null
            || systemType == null
            || facilityName == null) {
            return List.of();
        }

        String measurementName = resolveMeasurementName(primaryCell.metricName(), measurementBound);
        List<LocationDashboardImportedSample> samples = new ArrayList<>();
        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        if (primarySample != null
            && primarySample.sampledOn() != null
            && primarySample.resultValue() != null
            && !matchesWorksheetPrimarySample(primaryCell, primarySample)) {
            samples.add(new LocationDashboardCommentSample(
                LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY,
                primarySample.sampledOn(),
                primarySample.resultValue(),
                measurementName,
                facilityName,
                sublocation,
                systemType,
                measurementBound,
                resolvedBuilding,
                resolvedSystem,
                row == null ? null : row.pointOfUse(),
                row == null ? null : row.basis(),
                primaryCell.cellReference(),
                parsedComment,
                primarySample,
                "Primary Sample",
                buildCommentSampleIdentity("primary-sample", primarySample)
            ));
        }

        int sampleIndex = 1;
        for (LocationDashboardCommentParser.ParsedCommentSample sample : parsedComment.followUpSamples()) {
            if (sample == null || sample.sampledOn() == null || sample.resultValue() == null) {
                sampleIndex += 1;
                continue;
            }
            samples.add(new LocationDashboardCommentSample(
                LocationDashboardImportStrategy.SampleOrigin.COMMENT_SUPPLEMENTAL,
                sample.sampledOn(),
                sample.resultValue(),
                measurementName,
                facilityName,
                sublocation,
                systemType,
                measurementBound,
                resolvedBuilding,
                resolvedSystem,
                row == null ? null : row.pointOfUse(),
                row == null ? null : row.basis(),
                primaryCell.cellReference(),
                parsedComment,
                sample,
                "Supplemental Sample " + sampleIndex,
                buildCommentSampleIdentity("supplemental-sample-" + sampleIndex, sample)
            ));
            sampleIndex += 1;
        }
        return List.copyOf(samples);
    }

    private void validatePrimaryCommentSample(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row
    ) {
        if (parsedComment == null || !parsedComment.structured() || parsedComment.primarySample() == null) {
            return;
        }
        if (cell.numericValue() != null
            && parsedComment.hasMigratedLabeledTestNotes()
            && parsedComment.matchesWorksheetCompatibilityValue(cell.numericValue())) {
            return;
        }

        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        // The worksheet buckets samples by month, while structured comments can carry the
        // actual sample day inside that month. Keep the anchor loose enough for the workbook
        // we receive, but still reject comments that drift into a different reporting bucket.
        if (cell.observedDate() != null
            && primarySample.sampledOn() != null
            && !sameObservationMonth(cell.observedDate(), primarySample.sampledOn())) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": structured comment primary sample date must stay within the worksheet month bucket."
            );
        }
        if (cell.numericValue() != null
            && primarySample.resultValue() != null
            && cell.numericValue().compareTo(primarySample.resultValue()) != 0) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": structured comment primary sample result does not match the worksheet cell."
            );
        }
    }

    private boolean shouldCreateAnchoredCorrectiveAction(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardCommentParser.ParsedComment parsedComment,
        MeasurementBound measurementBound,
        SystemTypeConfig systemType
    ) {
        if (cell == null || measurementBound == null || systemType == null) {
            return false;
        }
        BigDecimal anchoredResult = anchoredCorrectiveActionResultValue(cell, parsedComment);
        return anchoredResult != null && !isCompliant(anchoredResult, measurementBound, systemType);
    }

    private BigDecimal anchoredCorrectiveActionResultValue(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardCommentParser.ParsedComment parsedComment
    ) {
        if (parsedComment != null && parsedComment.effectiveAnchoredResultValue() != null) {
            return parsedComment.effectiveAnchoredResultValue();
        }
        return cell == null ? null : cell.numericValue();
    }

    private boolean isCompliant(
        BigDecimal numericValue,
        MeasurementBound measurementBound,
        SystemTypeConfig systemType
    ) {
        if (numericValue == null || measurementBound == null || systemType == null) {
            return false;
        }
        return systemType.rangeProfile().isCompliant(numericValue, measurementBound);
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

    private SublocationConfig resolveSublocation(String facility, String building, SublocationConfig previousSublocation) {
        String normalizedFacility = normalizeKey(facility);
        if (normalizedFacility == null) {
            return previousSublocation;
        }

        List<SublocationConfig> facilitySublocations = sublocationsByFacilityAlias.getOrDefault(normalizedFacility, List.of());
        if (building != null) {
            String normalizedBuilding = normalizeKey(building);
            if (normalizedBuilding != null) {
                for (SublocationConfig sublocation : facilitySublocations) {
                    if (matchesAny(normalizedBuilding, sublocation.buildingAliases())) {
                        return sublocation;
                    }
                }
                SublocationConfig defaultSublocation = defaultSublocationsByFacilityAlias.get(normalizedFacility);
                if (defaultSublocation != null) {
                    return defaultSublocation;
                }
            }
        }

        for (SublocationConfig sublocation : config.sublocations()) {
            if (Objects.equals(normalizedFacility, normalizeKey(sublocation.key()))
                || Objects.equals(normalizedFacility, normalizeKey(sublocation.displayName()))) {
                return sublocation;
            }
        }

        if (building == null && belongsToFacility(previousSublocation, normalizedFacility)) {
            return previousSublocation;
        }
        return defaultSublocationsByFacilityAlias.get(normalizedFacility);
    }

    private boolean belongsToFacility(SublocationConfig sublocation, String normalizedFacility) {
        if (sublocation == null || normalizedFacility == null) {
            return false;
        }
        return matchesAny(normalizedFacility, effectiveFacilityAliases(sublocation));
    }

    private SystemTypeConfig resolveSystemType(String rawSystem, SystemTypeConfig previousSystemType) {
        String normalizedSystem = normalizeKey(rawSystem);
        if (normalizedSystem == null) {
            return previousSystemType;
        }

        // Hoag alias groups define the coercion contract for workbook spellings.
        // A raw value that is part of a configured alias group should collapse to
        // that group's representative system before we consider direct passthrough.
        SystemTypeAliasGroup aliasGroup = systemTypeAliasGroupsByAlias.get(normalizedSystem);
        if (aliasGroup != null && aliasGroup.representativeSystemType() != null) {
            return aliasGroup.representativeSystemType();
        }

        SystemTypeConfig resolvedSystemType = systemsByAlias.get(normalizedSystem);
        if (resolvedSystemType != null) {
            return resolvedSystemType;
        }

        // Only blank workbook cells inherit the prior row's system type. A nonblank
        // but unrecognized value should stay unresolved rather than contaminating the
        // previous system bucket.
        return null;
    }

    private java.util.Optional<CorrectiveActionDraft> buildCorrectiveActionDraft(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String resolvedFacility,
        String resolvedBuilding,
        String resolvedSystem,
        LocationDashboardCommentParser.ParsedComment parsedComment,
        String rawCommentText
    ) {
        String facilityName = resolveFacilityName(sublocation, resolvedFacility);
        String systemTypeName = resolveSystemTypeName(systemType, resolvedSystem);
        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            cell.metricName(),
            cell.observedDate(),
            sublocation,
            facilityName,
            resolvedBuilding,
            systemTypeName,
            row.pointOfUse(),
            row.basis(),
            null
        );
        List<String> commentLines = buildCommentDescriptionLines(parsedComment, rawCommentText);
        if (!commentLines.isEmpty()) {
            descriptionLines.add("");
            descriptionLines.addAll(commentLines);
        }

        String title = buildCorrectiveActionTitle(
            cell.metricName(),
            cell.observedDate()
        );
        return java.util.Optional.of(new CorrectiveActionDraft(
            cell.observedDate(),
            title,
            String.join("\n", descriptionLines),
            facilityName,
            systemTypeName,
            cell.metricName()
        ));
    }

    private List<CorrectiveActionDraft> buildCommentSampleCorrectiveActionDrafts(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String resolvedFacility,
        String resolvedBuilding,
        String resolvedSystem,
        MeasurementBound measurementBound,
        LocationDashboardCommentParser.ParsedComment parsedComment
    ) {
        if (parsedComment == null || measurementBound == null || systemType == null) {
            return List.of();
        }

        String facilityName = resolveFacilityName(sublocation, resolvedFacility);
        String systemTypeName = resolveSystemTypeName(systemType, resolvedSystem);
        String measurementName = resolveMeasurementName(cell.metricName(), measurementBound);
        List<CorrectiveActionDraft> drafts = new ArrayList<>();

        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        if (shouldCreatePrimaryCommentSampleCorrectiveAction(primarySample, cell, systemType, measurementBound)) {
            drafts.add(buildCommentSampleCorrectiveActionDraft(
                cell,
                row,
                sublocation,
                facilityName,
                resolvedBuilding,
                systemType,
                systemTypeName,
                measurementName,
                parsedComment,
                "Primary Sample",
                primarySample,
                buildCommentSampleIdentity("primary-sample", primarySample)
            ));
        }

        int sampleIndex = 1;
        for (LocationDashboardCommentParser.ParsedCommentSample sample : parsedComment.followUpSamples()) {
            if (!shouldCreateCommentSampleCorrectiveAction(sample, cell, systemType, measurementBound)) {
                sampleIndex += 1;
                continue;
            }
            drafts.add(buildCommentSampleCorrectiveActionDraft(
                cell,
                row,
                sublocation,
                facilityName,
                resolvedBuilding,
                systemType,
                systemTypeName,
                measurementName,
                parsedComment,
                "Supplemental Sample " + sampleIndex,
                sample,
                buildCommentSampleIdentity("supplemental-sample-" + sampleIndex, sample)
            ));
            sampleIndex += 1;
        }

        return List.copyOf(drafts);
    }

    private java.util.Optional<CorrectiveActionDraft> buildSyntheticCorrectiveActionDraft(
        LocationDashboardWorksheetSample sample
    ) {
        if (sample == null || sample.measurementName() == null) {
            return java.util.Optional.empty();
        }

        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            sample.measurementName(),
            sample.observedDate(),
            sample.sublocation(),
            sample.facilityName(),
            sample.resolvedBuilding(),
            sample.systemTypeName(),
            sample.pointOfUse(),
            sample.basis(),
            null
        );
        descriptionLines.add("");
        descriptionLines.add("Note: Imported from an out-of-spec worksheet sample without cell comment metadata.");

        return java.util.Optional.of(new CorrectiveActionDraft(
            sample.observedDate(),
            buildSyntheticCorrectiveActionTitle(sample.measurementName(), sample.observedDate()),
            String.join("\n", descriptionLines),
            sample.facilityName(),
            sample.systemTypeName(),
            sample.measurementName()
        ));
    }

    private java.util.Optional<CorrectiveActionDraft> buildSyntheticCorrectiveActionDraft(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String resolvedFacility,
        String resolvedBuilding,
        String resolvedSystem,
        String measurementName
    ) {
        String facilityName = resolveFacilityName(sublocation, resolvedFacility);
        String systemTypeName = resolveSystemTypeName(systemType, resolvedSystem);
        if (measurementName == null) {
            return java.util.Optional.empty();
        }

        // A failed worksheet sample is a non-conformance even when the analyst never
        // attached a comment to that exact cell. Promote every raw failed cell into
        // a corrective action so derived incident totals stay sample-complete.
        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            measurementName,
            cell.observedDate(),
            sublocation,
            facilityName,
            resolvedBuilding,
            systemTypeName,
            row.pointOfUse(),
            row.basis(),
            null
        );
        descriptionLines.add("");
        descriptionLines.add("Note: Imported from an out-of-spec worksheet sample without cell comment metadata.");

        return java.util.Optional.of(new CorrectiveActionDraft(
            cell.observedDate(),
            buildSyntheticCorrectiveActionTitle(measurementName, cell.observedDate()),
            String.join("\n", descriptionLines),
            facilityName,
            systemTypeName,
            measurementName
        ));
    }

    private LocationDashboardCommentParser.ParsedComment parseComment(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row
    ) {
        try {
            return commentParser.parse(cell.commentText());
        } catch (IllegalArgumentException ex) {
            throw invalidSpreadsheet(
                "Row " + row.rowNumber() + (cell.cellReference() == null ? "" : " cell " + cell.cellReference())
                    + ": " + ex.getMessage()
            );
        }
    }

    private List<String> buildCommentDescriptionLines(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        String rawCommentText
    ) {
        if (parsedComment == null || !parsedComment.hasMeaningfulContent()) {
            return formatCommentLines(rawCommentText);
        }

        List<String> lines = new ArrayList<>();
        if (parsedComment.sampleLocation() != null) {
            lines.add("Sample Location: " + parsedComment.sampleLocation());
        }
        if (parsedComment.primarySample() != null) {
            appendSampleSummary(lines, "Primary Sample", parsedComment.primarySample());
            appendSampleDetails(lines, "Primary Sample", parsedComment.primarySample());
        }
        appendNotes(lines, parsedComment.notes(), null);
        appendCorrectiveActions(lines, parsedComment.correctiveActions(), null);

        int sampleIndex = 1;
        for (LocationDashboardCommentParser.ParsedCommentSample sample : parsedComment.followUpSamples()) {
            appendSampleSummary(lines, "Supplemental Sample " + sampleIndex, sample);
            appendSampleDetails(lines, "Supplemental Sample " + sampleIndex, sample);
            sampleIndex += 1;
        }
        return lines;
    }

    private List<String> buildCommentSampleDescriptionLines(
        LocationDashboardCommentParser.ParsedComment parsedComment,
        String sampleLabel,
        LocationDashboardCommentParser.ParsedCommentSample sample
    ) {
        if (parsedComment == null || sample == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        if (parsedComment.sampleLocation() != null) {
            lines.add("Sample Location: " + parsedComment.sampleLocation());
        }
        appendSampleSummary(lines, sampleLabel, sample);
        appendSampleDetails(lines, sampleLabel, sample);
        appendNotes(lines, parsedComment.notes(), null);
        appendCorrectiveActions(lines, parsedComment.correctiveActions(), null);
        return lines;
    }

    private void appendSampleSummary(
        List<String> lines,
        String label,
        LocationDashboardCommentParser.ParsedCommentSample sample
    ) {
        if (sample == null || sample.sampledOn() == null) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        summary.append(label).append(": sampled on ").append(sample.sampledOn());
        if (sample.resultReceivedOn() != null) {
            summary.append("; result received on ").append(sample.resultReceivedOn());
        }
        if (sample.resultRaw() != null) {
            summary.append("; result ").append(sample.resultRaw());
        }
        lines.add(summary.toString());
    }

    private void appendSampleDetails(
        List<String> lines,
        String label,
        LocationDashboardCommentParser.ParsedCommentSample sample
    ) {
        if (sample == null) {
            return;
        }
        appendNotes(lines, sample.notes(), label);
        appendCorrectiveActions(lines, sample.correctiveActions(), label);
    }

    private void appendNotes(List<String> lines, List<String> notes, String prefix) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        for (String note : notes) {
            if (note == null || note.isBlank()) {
                continue;
            }
            lines.add((prefix == null ? "Note" : prefix + " Note") + ": " + note);
        }
    }

    private void appendCorrectiveActions(
        List<String> lines,
        List<LocationDashboardCommentParser.ParsedCommentCorrectiveAction> actions,
        String prefix
    ) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (LocationDashboardCommentParser.ParsedCommentCorrectiveAction action : actions) {
            if (action == null || action.text() == null || action.text().isBlank()) {
                continue;
            }
            String actionPrefix = prefix == null ? "CA" : prefix + " CA";
            lines.add(actionPrefix + ": " + action.text());
            if (action.actionDate() != null) {
                lines.add(actionPrefix + " Date: " + action.actionDate());
            }
            if (action.ticket() != null) {
                lines.add(actionPrefix + " Ticket: " + action.ticket());
            }
            appendNotes(lines, action.notes(), actionPrefix);
        }
    }

    private boolean hasUsableCommentPayload(String rawCommentText) {
        if (rawCommentText == null || rawCommentText.isBlank()) {
            return false;
        }
        String normalized = rawCommentText.replace("\r\n", "\n").strip();
        int markerIndex = normalized.indexOf("Comment:");
        String payload = markerIndex >= 0
            ? normalized.substring(markerIndex + "Comment:".length()).strip()
            : normalized;
        if (payload.isBlank()) {
            return false;
        }
        return true;
    }

    private boolean shouldCreatePrimaryCommentSampleCorrectiveAction(
        LocationDashboardCommentParser.ParsedCommentSample primarySample,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        SystemTypeConfig systemType,
        MeasurementBound measurementBound
    ) {
        if (matchesWorksheetPrimarySample(primaryCell, primarySample)) {
            return false;
        }
        return shouldCreateCommentSampleCorrectiveAction(primarySample, primaryCell, systemType, measurementBound);
    }

    private boolean shouldCreateCommentSampleCorrectiveAction(
        LocationDashboardCommentParser.ParsedCommentSample sample,
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        SystemTypeConfig systemType,
        MeasurementBound measurementBound
    ) {
        if (sample == null
            || sample.sampledOn() == null
            || sample.resultValue() == null
            || systemType == null
            || measurementBound == null) {
            return false;
        }
        // The worksheet cell already anchors the primary corrective action for its own
        // sample. Only distinct failed comment samples should create additional incidents.
        if (matchesPrimaryCellSample(primaryCell, sample)) {
            return false;
        }
        return !isCompliant(sample.resultValue(), measurementBound, systemType);
    }

    private boolean matchesWorksheetPrimarySample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardCommentParser.ParsedCommentSample candidateSample
    ) {
        if (primaryCell == null || candidateSample == null) {
            return false;
        }
        if (primaryCell.observedDate() == null || candidateSample.sampledOn() == null) {
            return false;
        }
        // Primary structured samples often preserve the actual sample day while the worksheet cell
        // represents the reporting month bucket for that same sample.
        if (!sameObservationMonth(primaryCell.observedDate(), candidateSample.sampledOn())) {
            return false;
        }
        if (primaryCell.numericValue() == null || candidateSample.resultValue() == null) {
            return false;
        }
        return primaryCell.numericValue().compareTo(candidateSample.resultValue()) == 0;
    }

    private boolean matchesPrimaryCellSample(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell primaryCell,
        LocationDashboardCommentParser.ParsedCommentSample candidateSample
    ) {
        if (primaryCell == null || candidateSample == null) {
            return false;
        }
        if (primaryCell.observedDate() == null || candidateSample.sampledOn() == null) {
            return false;
        }
        if (!Objects.equals(primaryCell.observedDate(), candidateSample.sampledOn())) {
            return false;
        }
        if (primaryCell.numericValue() == null || candidateSample.resultValue() == null) {
            return false;
        }
        return primaryCell.numericValue().compareTo(candidateSample.resultValue()) == 0;
    }

    private boolean sameObservationMonth(LocalDate worksheetObservedDate, LocalDate commentSampleDate) {
        if (worksheetObservedDate == null || commentSampleDate == null) {
            return false;
        }
        return worksheetObservedDate.getYear() == commentSampleDate.getYear()
            && worksheetObservedDate.getMonth() == commentSampleDate.getMonth();
    }

    private String resolveMeasurementName(String workbookMetricName, MeasurementBound measurementBound) {
        if (workbookMetricName != null && !workbookMetricName.isBlank()) {
            return workbookMetricName.strip();
        }
        if (measurementBound == null || measurementBound.getMeasurementName() == null) {
            return null;
        }
        String fallbackMeasurementName = measurementBound.getMeasurementName().strip();
        return fallbackMeasurementName.isBlank() ? null : fallbackMeasurementName;
    }

    private CorrectiveActionDraft buildCommentSampleCorrectiveActionDraft(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        String facilityName,
        String resolvedBuilding,
        SystemTypeConfig systemType,
        String systemTypeName,
        String measurementName,
        LocationDashboardCommentParser.ParsedComment parsedComment,
        String sampleLabel,
        LocationDashboardCommentParser.ParsedCommentSample sample,
        String sampleIdentity
    ) {
        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            measurementName,
            sample.sampledOn(),
            sublocation,
            facilityName,
            resolvedBuilding,
            systemTypeName,
            row.pointOfUse(),
            row.basis(),
            sampleIdentity
        );
        List<String> commentLines = buildCommentSampleDescriptionLines(parsedComment, sampleLabel, sample);
        if (!commentLines.isEmpty()) {
            descriptionLines.add("");
            descriptionLines.addAll(commentLines);
        }

        return new CorrectiveActionDraft(
            sample.sampledOn(),
            buildSupplementalCorrectiveActionTitle(
                measurementName,
                sample.sampledOn(),
                sampleLabel
            ),
            String.join("\n", descriptionLines),
            facilityName,
            systemTypeName,
            measurementName
        );
    }

    private List<String> buildCorrectiveActionDescriptionLines(
        String measurementName,
        LocalDate observedDate,
        SublocationConfig sublocation,
        String facilityName,
        String resolvedBuilding,
        String systemTypeName,
        String pointOfUse,
        String basis,
        String sampleIdentity
    ) {
        List<String> descriptionLines = new ArrayList<>();
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine(measurementName)
        );
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(observedDate)
        );
        if (sublocation != null && sublocation.displayName() != null && !sublocation.displayName().isBlank()) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.sublocationLine(sublocation.displayName())
            );
        } else if (facilityName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.facilityLine(facilityName)
            );
        }
        if (resolvedBuilding != null && !resolvedBuilding.isBlank()) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.buildingLine(resolvedBuilding)
            );
        }
        if (systemTypeName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.systemLine(systemTypeName)
            );
        }
        if (pointOfUse != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.pointOfUseLine(pointOfUse)
            );
        }
        if (basis != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.basisLine(basis)
            );
        }
        if (sampleIdentity != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.sampleIdentityLine(sampleIdentity)
            );
        }
        return descriptionLines;
    }

    private String buildCorrectiveActionTitle(
        String metricName,
        LocalDate observedDate
    ) {
        return readableCorrectiveActionTitle(metricName, observedDate, null);
    }

    private String buildSyntheticCorrectiveActionTitle(
        String metricName,
        LocalDate observedDate
    ) {
        return readableCorrectiveActionTitle(metricName, observedDate, null);
    }

    private String buildSupplementalCorrectiveActionTitle(
        String metricName,
        LocalDate observedDate,
        String sampleLabel
    ) {
        return readableCorrectiveActionTitle(metricName, observedDate, sampleLabel);
    }

    private String buildCommentSampleIdentity(
        String sampleKind,
        LocationDashboardCommentParser.ParsedCommentSample sample
    ) {
        return String.join("|", List.of(
            nullSafe(sampleKind),
            nullSafe(String.valueOf(sample == null ? null : sample.sampledOn())),
            nullSafe(String.valueOf(sample == null ? null : sample.resultReceivedOn())),
            nullSafe(sample == null ? null : sample.resultRaw())
        ));
    }

    private void addDescriptionLine(List<String> descriptionLines, String value) {
        if (value != null) {
            descriptionLines.add(value);
        }
    }

    private String readableCorrectiveActionTitle(String metricName, LocalDate observedDate, String suffix) {
        String normalizedMetricName = metricName == null || metricName.isBlank() ? "Comment" : metricName.strip();
        String normalizedDate = observedDate == null ? "undated" : observedDate.toString();
        String normalizedSuffix = suffix == null || suffix.isBlank() ? null : suffix.strip();
        return normalizedSuffix == null
            ? "CA: " + normalizedMetricName + " " + normalizedDate
            : "CA: " + normalizedMetricName + " " + normalizedDate + " " + normalizedSuffix;
    }

    private List<String> formatCommentLines(String rawCommentText) {
        if (rawCommentText == null || rawCommentText.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : rawCommentText.split(";")) {
            String normalizedToken = token == null ? null : token.strip();
            if (normalizedToken != null && !normalizedToken.isBlank()) {
                tokens.add(normalizedToken);
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index += 2) {
            String label = tokens.get(index);
            if (index + 1 >= tokens.size()) {
                lines.add(label);
                continue;
            }
            String value = tokens.get(index + 1);
            if (label.endsWith(":")) {
                lines.add(label + " " + value);
            } else {
                lines.add(label + ": " + value);
            }
        }
        return lines;
    }

    private boolean matchesAny(String candidate, List<String> rawAliases) {
        if (candidate == null) {
            return false;
        }
        if (rawAliases == null || rawAliases.isEmpty()) {
            return false;
        }
        String normalizedCandidate = normalizeKey(candidate);
        for (String rawAlias : rawAliases) {
            if (Objects.equals(normalizedCandidate, normalizeKey(rawAlias))) {
                return true;
            }
        }
        return false;
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

    private String resolveFacilityName(SublocationConfig sublocation, String resolvedFacility) {
        if (sublocation != null && sublocation.displayName() != null && !sublocation.displayName().isBlank()) {
            return sublocation.displayName();
        }
        if (resolvedFacility == null) {
            return null;
        }
        String normalized = resolvedFacility.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveSystemTypeName(SystemTypeConfig systemType, String resolvedSystem) {
        if (systemType != null && systemType.displayName() != null && !systemType.displayName().isBlank()) {
            return systemType.displayName();
        }
        if (resolvedSystem == null) {
            return null;
        }
        String normalized = resolvedSystem.strip();
        return normalized.isBlank() ? null : normalized;
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

    private List<CorrectiveActionDraft> deduplicateCorrectiveActions(List<CorrectiveActionDraft> drafts) {
        Map<String, CorrectiveActionDraft> draftsByIdentity = new LinkedHashMap<>();
        for (CorrectiveActionDraft draft : drafts) {
            String identity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                draft.title(),
                draft.description()
            );
            draftsByIdentity.putIfAbsent(identity, draft);
        }
        return List.copyOf(draftsByIdentity.values());
    }

    private record SystemTypeAliasGroup(
        SystemTypeConfig representativeSystemType
    ) {
    }

    private ApiClientException invalidSpreadsheet(String message) {
        return new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_file_invalid",
                message
        );
    }
}
