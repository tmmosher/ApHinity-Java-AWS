package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SublocationConfig;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.SystemTypeConfig;

final class LocationDashboardImportContextResolver {
    private final LocationDashboardImportStrategyConfig config;
    private final Map<String, SystemTypeConfig> systemsByAlias;
    private final Map<String, SystemTypeAliasGroup> systemTypeAliasGroupsByAlias;
    private final Map<String, List<SublocationConfig>> sublocationsByFacilityAlias;
    private final Map<String, SublocationConfig> defaultSublocationsByFacilityAlias;

    LocationDashboardImportContextResolver(
        LocationDashboardImportStrategyConfig config,
        Map<String, SystemTypeConfig> systemsByAlias,
        Map<String, SystemTypeAliasGroup> systemTypeAliasGroupsByAlias,
        Map<String, List<SublocationConfig>> sublocationsByFacilityAlias,
        Map<String, SublocationConfig> defaultSublocationsByFacilityAlias
    ) {
        this.config = config;
        this.systemsByAlias = systemsByAlias == null ? Map.of() : Map.copyOf(systemsByAlias);
        this.systemTypeAliasGroupsByAlias = systemTypeAliasGroupsByAlias == null
            ? Map.of()
            : Map.copyOf(systemTypeAliasGroupsByAlias);
        this.sublocationsByFacilityAlias = sublocationsByFacilityAlias == null
            ? Map.of()
            : copySublocationMap(sublocationsByFacilityAlias);
        this.defaultSublocationsByFacilityAlias = defaultSublocationsByFacilityAlias == null
            ? Map.of()
            : Map.copyOf(defaultSublocationsByFacilityAlias);
    }

    ActiveImportContext emptyContext() {
        return new ActiveImportContext(null, null, null, null, null);
    }

    RowImportContext resolveRowContext(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        ActiveImportContext activeContext
    ) {
        String rowFacility = row == null ? null : row.facility();
        String rowBuilding = row == null ? null : row.building();
        String rowSystem = row == null ? null : row.system();
        String resolvedFacility = rowFacility != null ? rowFacility : activeContext.activeFacility();
        String resolvedBuilding = rowBuilding != null ? rowBuilding : activeContext.activeBuilding();
        String resolvedSystem = rowSystem != null ? rowSystem : activeContext.activeSystemRaw();
        SublocationConfig sublocation = resolveSublocation(
            resolvedFacility,
            resolvedBuilding,
            activeContext.activeSublocation()
        );
        SystemTypeConfig systemType = resolveSystemType(resolvedSystem, activeContext.activeSystemType());
        return new RowImportContext(
            resolvedFacility,
            resolvedBuilding,
            resolvedSystem,
            sublocation,
            systemType,
            resolveFacilityName(sublocation, resolvedFacility)
        );
    }

    ActiveImportContext advance(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        RowImportContext rowContext,
        ActiveImportContext activeContext
    ) {
        String activeFacility = activeContext.activeFacility();
        String activeBuilding = activeContext.activeBuilding();
        String activeSystemRaw = activeContext.activeSystemRaw();
        SublocationConfig activeSublocation = activeContext.activeSublocation();
        SystemTypeConfig activeSystemType = activeContext.activeSystemType();

        if (rowContext.sublocation() != null) {
            if (row != null
                && row.facility() != null
                && (Objects.equals(normalizeKey(row.facility()), normalizeKey(rowContext.sublocation().key()))
                    || Objects.equals(normalizeKey(row.facility()), normalizeKey(rowContext.sublocation().displayName()))
                    || matchesAny(row.facility(), rowContext.sublocation().facilityAliases()))) {
                activeFacility = rowContext.resolvedFacility();
            }
            if (row != null && row.building() != null) {
                activeBuilding = row.building();
            }
            activeSublocation = rowContext.sublocation();
        }
        if (rowContext.systemType() != null) {
            activeSystemRaw = rowContext.resolvedSystem();
            activeSystemType = rowContext.systemType();
        }

        return new ActiveImportContext(
            activeFacility,
            activeBuilding,
            activeSystemRaw,
            activeSublocation,
            activeSystemType
        );
    }

    String canonicalFacilityName(String rawFacility, String rawBuilding) {
        return resolveFacilityName(resolveSublocation(rawFacility, rawBuilding, null), rawFacility);
    }

    String canonicalSystemTypeName(String rawSystem) {
        SystemTypeConfig systemType = resolveSystemType(rawSystem, null);
        return systemType == null ? null : resolveSystemTypeName(systemType, rawSystem);
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

        // Some newer ST-108 exports place building/site identifiers like "SPD-16405"
        // into the facility column. When that happens, allow the facility token to
        // resolve through configured building aliases so sample facility names still
        // collapse to the canonical sublocation display name.
        for (SublocationConfig sublocation : config.sublocations()) {
            if (matchesAny(normalizedFacility, sublocation.buildingAliases())) {
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

        SystemTypeAliasGroup aliasGroup = systemTypeAliasGroupsByAlias.get(normalizedSystem);
        if (aliasGroup != null && aliasGroup.representativeSystemType() != null) {
            return aliasGroup.representativeSystemType();
        }

        return systemsByAlias.get(normalizedSystem);
    }

    private boolean matchesAny(String candidate, List<String> rawAliases) {
        if (candidate == null || rawAliases == null || rawAliases.isEmpty()) {
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

    private Map<String, List<SublocationConfig>> copySublocationMap(Map<String, List<SublocationConfig>> source) {
        Map<String, List<SublocationConfig>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
    }

    record ActiveImportContext(
        String activeFacility,
        String activeBuilding,
        String activeSystemRaw,
        SublocationConfig activeSublocation,
        SystemTypeConfig activeSystemType
    ) {
    }

    record RowImportContext(
        String resolvedFacility,
        String resolvedBuilding,
        String resolvedSystem,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String facilityName
    ) {
    }

    record SystemTypeAliasGroup(
        SystemTypeConfig representativeSystemType
    ) {
    }
}
