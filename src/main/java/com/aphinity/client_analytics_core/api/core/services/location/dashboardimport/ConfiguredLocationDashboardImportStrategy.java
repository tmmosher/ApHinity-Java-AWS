package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.GraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

/**
 * Generic location import strategy backed by a JSON configuration file.
 * The strategy keeps location-specific grouping rules in configuration while the import
 * behavior stays centralized and testable.
 */
public class ConfiguredLocationDashboardImportStrategy implements LocationDashboardImportStrategy {
    private static final List<String> DEFAULT_TRACE_COLORS = List.of(
        "#1f77b4",
        "#ff7f0e",
        "#2ca02c",
        "#d62728",
        "#9467bd",
        "#8c564b",
        "#e377c2",
        "#7f7f7f",
        "#bcbd22",
        "#17becf"
    );

    private final LocationDashboardImportStrategyConfig config;
    private final Map<String, SystemTypeConfig> systemsByAlias;
    private final Map<String, List<SublocationConfig>> sublocationsByFacilityAlias;
    private final Map<String, SublocationConfig> defaultSublocationsByFacilityAlias;
    private final Map<String, GraphConfig> graphDefinitionsById;
    private final Map<String, List<GraphConfig>> graphsBySublocationKey;

    public ConfiguredLocationDashboardImportStrategy(LocationDashboardImportStrategyConfig config) {
        this.config = validate(config);
        this.systemsByAlias = buildSystemsByAlias(this.config.systems());
        this.sublocationsByFacilityAlias = buildSublocationsByFacilityAlias(this.config.sublocations());
        this.defaultSublocationsByFacilityAlias = buildDefaultSublocationsByFacilityAlias(this.config.sublocations());
        this.graphDefinitionsById = buildGraphDefinitionsById(this.config.graphs());
        this.graphsBySublocationKey = buildGraphsBySublocationKey(this.config.graphs());
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

        Map<String, GraphAggregation> aggregationsByGraphId = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : config.graphs()) {
            aggregationsByGraphId.put(graphDefinition.id(), new GraphAggregation());
        }

        List<ImportedObservation> observations = new ArrayList<>();
        List<CorrectiveActionDraft> correctiveActions = new ArrayList<>();
        String activeFacility = null;
        String activeBuilding = null;
        String activeSystem = null;
        SublocationConfig activeSublocation = null;
        SystemTypeConfig activeSystemType = null;

        for (LocationDashboardSpreadsheetParser.ParsedDashboardRow row : workbook.rows()) {
            if (row.facility() != null) {
                activeFacility = row.facility();
            }
            if (row.building() != null) {
                activeBuilding = row.building();
            }
            if (row.system() != null) {
                activeSystem = row.system();
            }

            String resolvedFacility = activeFacility;
            String resolvedBuilding = row.building() != null ? row.building() : activeBuilding;
            String resolvedSystem = row.system() != null ? row.system() : activeSystem;

            activeSublocation = resolveSublocation(resolvedFacility, resolvedBuilding, activeSublocation);
            activeSystemType = resolveSystemType(resolvedSystem, activeSystemType);

            for (LocationDashboardSpreadsheetParser.ParsedDashboardCell cell : row.cells()) {
                if (cell.commentText() != null) {
                    buildCorrectiveActionDraft(
                        cell,
                        row,
                        activeSublocation,
                        activeSystemType,
                        resolvedFacility,
                        resolvedBuilding,
                        resolvedSystem
                    ).ifPresent(correctiveActions::add);
                }

                if (cell.numericValue() == null || activeSystemType == null) {
                    continue;
                }
                MeasurementBound measurementBound = measurementBoundsByName.get(normalizeKey(cell.metricName()));
                if (measurementBound == null) {
                    continue;
                }
                boolean compliant = activeSystemType.rangeProfile().isCompliant(cell.numericValue(), measurementBound);
                String facilityName = resolveFacilityName(activeSublocation, resolvedFacility);
                if (facilityName != null) {
                    observations.add(new ImportedObservation(
                        cell.observedDate(),
                        facilityName,
                        activeSystemType.displayName(),
                        measurementBound.getMeasurementName(),
                        compliant
                    ));
                }

                if (activeSublocation != null) {
                    List<GraphConfig> graphDefinitions = graphsBySublocationKey.getOrDefault(
                        normalizeKey(activeSublocation.key()),
                        List.of()
                    );
                    for (GraphConfig graphDefinition : graphDefinitions) {
                        String traceName = switch (graphDefinition.importType()) {
                            case SYSTEM_TYPE_COMPLIANCE -> activeSystemType.displayName();
                            case WATER_QUALITY_COMPLIANCE -> measurementBound.getMeasurementName();
                        };
                        aggregationsByGraphId.get(graphDefinition.id()).record(traceName, cell.observedDate(), compliant);
                    }
                }
            }
        }

        List<ComputedGraphPayload> computedGraphs = new ArrayList<>();
        for (GraphConfig graphDefinition : config.graphs()) {
            GraphAggregation graphAggregation = aggregationsByGraphId.get(graphDefinition.id());
            computedGraphs.add(new ComputedGraphPayload(
                graphDefinition.id(),
                buildGraphData(graphDefinition, graphAggregation)
            ));
        }

        return new LocationDashboardImportComputation(
            List.copyOf(computedGraphs),
            List.copyOf(observations),
            List.copyOf(deduplicateCorrectiveActions(correctiveActions))
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

        for (SystemTypeConfig system : rawConfig.systems()) {
            if (normalizeKey(system.key()) == null) {
                throw new IllegalStateException("Dashboard import strategy system keys are required");
            }
            if (system.rangeProfile() == null) {
                throw new IllegalStateException(
                    "Dashboard import strategy systems must declare a range profile: " + system.key()
                );
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

    private Map<String, GraphConfig> buildGraphDefinitionsById(List<GraphConfig> graphDefinitions) {
        Map<String, GraphConfig> graphDefinitionsById = new LinkedHashMap<>();
        for (GraphConfig graphDefinition : graphDefinitions) {
            graphDefinitionsById.put(normalizeKey(graphDefinition.id()), graphDefinition);
        }
        return Map.copyOf(graphDefinitionsById);
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
        if (systemsByAlias.putIfAbsent(normalizedAlias, systemType) != null) {
            throw new IllegalStateException("Dashboard import system aliases must be unique: " + rawAlias);
        }
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
            }
        }

        if (belongsToFacility(previousSublocation, normalizedFacility)) {
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
        return systemsByAlias.getOrDefault(normalizedSystem, previousSystemType);
    }

    private java.util.Optional<CorrectiveActionDraft> buildCorrectiveActionDraft(
        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell,
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String resolvedFacility,
        String resolvedBuilding,
        String resolvedSystem
    ) {
        List<String> formattedCommentLines = formatCommentLines(cell.commentText());

        List<String> descriptionLines = new ArrayList<>();
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine(cell.metricName())
        );
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(cell.observedDate())
        );
        String facilityName = resolveFacilityName(sublocation, resolvedFacility);
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
        String systemTypeName = resolveSystemTypeName(systemType, resolvedSystem);
        if (systemType != null && systemType.displayName() != null && !systemType.displayName().isBlank()) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.systemLine(systemType.displayName())
            );
        } else if (systemTypeName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.systemLine(systemTypeName)
            );
        }
        if (row.pointOfUse() != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.pointOfUseLine(row.pointOfUse())
            );
        }
        if (row.basis() != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.basisLine(row.basis())
            );
        }
        if (!formattedCommentLines.isEmpty()) {
            descriptionLines.add("");
            descriptionLines.addAll(formattedCommentLines);
        }

        String title = buildCorrectiveActionTitle(
            cell.metricName(),
            cell.observedDate(),
            sublocation == null ? null : sublocation.key(),
            systemType == null ? null : systemType.key(),
            row.pointOfUse(),
            row.basis()
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

    private String buildCorrectiveActionTitle(
        String metricName,
        LocalDate observedDate,
        String sublocationKey,
        String systemKey,
        String pointOfUse,
        String basis
    ) {
        String abbreviatedMetricName = abbreviate(metricName, 16);
        String fingerprint = buildFingerprint(String.join("|", List.of(
            nullSafe(metricName),
            nullSafe(String.valueOf(observedDate)),
            nullSafe(sublocationKey),
            nullSafe(systemKey),
            nullSafe(pointOfUse),
            nullSafe(basis)
        )));
        return "CA: " + abbreviatedMetricName + " " + observedDate + " " + fingerprint;
    }

    private void addDescriptionLine(List<String> descriptionLines, String value) {
        if (value != null) {
            descriptionLines.add(value);
        }
    }

    private String buildFingerprint(String rawIdentity) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = rawIdentity.getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return String.format(Locale.ROOT, "%04X", crc32.getValue() & 0xFFFF);
    }

    private String abbreviate(String rawValue, int maxLength) {
        if (rawValue == null) {
            return "Comment";
        }
        String normalized = rawValue.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
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
        return List.copyOf(lines);
    }

    private List<Map<String, Object>> buildGraphData(GraphConfig graphDefinition, GraphAggregation graphAggregation) {
        List<String> traceOrder = new ArrayList<>();
        if (graphDefinition.traceOrder() != null) {
            for (String traceName : graphDefinition.traceOrder()) {
                if (traceName != null && !traceName.isBlank()) {
                    traceOrder.add(traceName.strip());
                }
            }
        }
        for (String traceName : graphAggregation.traceNames()) {
            if (!containsNormalized(traceOrder, traceName)) {
                traceOrder.add(traceName);
            }
        }
        if (traceOrder.isEmpty()) {
            traceOrder.addAll(graphAggregation.traceNames());
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        int traceIndex = 0;
        for (String traceName : traceOrder) {
            List<LocalDate> observedDates = graphAggregation.observedDatesForTrace(traceName).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
            List<String> xValues = observedDates.stream()
                .map(LocalDate::toString)
                .toList();
            List<Double> yValues = observedDates.stream()
                .map(observedDate -> graphAggregation.percentage(traceName, observedDate))
                .toList();
            List<Map<String, Object>> customData = observedDates.stream()
                .map(observedDate -> Map.<String, Object>of(
                    "sampleCount", graphAggregation.total(traceName, observedDate),
                    "compliantCount", graphAggregation.compliant(traceName, observedDate),
                    "nonConformingCount", graphAggregation.nonConforming(traceName, observedDate)
                ))
                .toList();
            traces.add(new LinkedHashMap<>(Map.of(
                "type", "scatter",
                "name", traceName,
                "x", xValues,
                "y", yValues,
                "customdata", customData,
                "mode", "lines+markers",
                "line", Map.of(
                    "color", resolveTraceColor(graphDefinition, traceName, traceIndex),
                    "width", 2
                ),
                "marker", Map.of("size", 6)
            )));
            traceIndex += 1;
        }
        return List.copyOf(traces);
    }

    private String resolveTraceColor(GraphConfig graphDefinition, String traceName, int traceIndex) {
        if (graphDefinition.traceColors() != null) {
            for (Map.Entry<String, String> entry : graphDefinition.traceColors().entrySet()) {
                if (Objects.equals(normalizeKey(entry.getKey()), normalizeKey(traceName))) {
                    return entry.getValue();
                }
            }
        }
        return DEFAULT_TRACE_COLORS.get(traceIndex % DEFAULT_TRACE_COLORS.size());
    }

    private boolean containsNormalized(Collection<String> values, String candidate) {
        String normalizedCandidate = normalizeKey(candidate);
        for (String value : values) {
            if (Objects.equals(normalizeKey(value), normalizedCandidate)) {
                return true;
            }
        }
        return false;
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

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
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

    private List<CorrectiveActionDraft> deduplicateCorrectiveActions(List<CorrectiveActionDraft> drafts) {
        Map<String, CorrectiveActionDraft> draftsByIdentity = new LinkedHashMap<>();
        for (CorrectiveActionDraft draft : drafts) {
            String identity = draft.observedDate() + "|" + normalizeKey(draft.title());
            draftsByIdentity.putIfAbsent(identity, draft);
        }
        return List.copyOf(draftsByIdentity.values());
    }

    private static final class GraphAggregation {
        private final Map<String, Map<LocalDate, ComplianceCounter>> countersByTrace = new LinkedHashMap<>();

        void record(String traceName, LocalDate observedDate, boolean compliant) {
            countersByTrace
                .computeIfAbsent(traceName, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(observedDate, ignored -> new ComplianceCounter())
                .record(compliant);
        }

        Set<String> traceNames() {
            return countersByTrace.keySet();
        }

        Set<LocalDate> observedDatesForTrace(String traceName) {
            return countersByTrace.getOrDefault(traceName, Map.of()).keySet();
        }

        double percentage(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = countersByTrace
                .getOrDefault(traceName, Map.of())
                .get(observedDate);
            if (counter == null || counter.total == 0) {
                return 0.0d;
            }
            return (counter.compliant * 100.0d) / counter.total;
        }

        int total(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = countersByTrace
                .getOrDefault(traceName, Map.of())
                .get(observedDate);
            return counter == null ? 0 : counter.total;
        }

        int compliant(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = countersByTrace
                .getOrDefault(traceName, Map.of())
                .get(observedDate);
            return counter == null ? 0 : counter.compliant;
        }

        int nonConforming(String traceName, LocalDate observedDate) {
            ComplianceCounter counter = countersByTrace
                .getOrDefault(traceName, Map.of())
                .get(observedDate);
            if (counter == null) {
                return 0;
            }
            return counter.total - counter.compliant;
        }
    }

    private static final class ComplianceCounter {
        private int total;
        private int compliant;

        void record(boolean pointCompliant) {
            total += 1;
            if (pointCompliant) {
                compliant += 1;
            }
        }
    }
}
