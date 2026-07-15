package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

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
        return new ActiveImportContext(Map.of(), null, null, null);
    }

    RowImportContext resolveRowContext(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        ActiveImportContext activeContext
    ) {
        ActiveImportContext effectiveActiveContext = activeContext == null ? emptyContext() : activeContext;
        Map<String, String> identityValues = mergeIdentityValues(
            effectiveActiveContext.activeIdentityValues(),
            row == null ? Map.of() : row.identityValues()
        );
        SublocationConfig sublocation = resolveSublocation(
            identityValues,
            effectiveActiveContext.activeSublocation()
        );
        SystemResolution systemResolution = resolveSystemType(
            row == null ? Map.of() : row.identityValues(),
            effectiveActiveContext
        );
        return new RowImportContext(
            identityValues,
            sublocation,
            systemResolution.systemType(),
            systemResolution.identityKey(),
            resolveFacilityName(
                sublocation,
                firstIdentityValue(identityValues, systemResolution.identityKey())
            )
        );
    }

    ActiveImportContext advance(
        LocationDashboardSpreadsheetParser.ParsedDashboardRow row,
        RowImportContext rowContext,
        ActiveImportContext activeContext
    ) {
        if (rowContext == null) {
            return activeContext == null ? emptyContext() : activeContext;
        }
        ActiveImportContext effectiveActiveContext = activeContext == null ? emptyContext() : activeContext;
        return new ActiveImportContext(
            rowContext.identityValues(),
            rowContext.sublocation(),
            rowContext.systemType() == null ? effectiveActiveContext.activeSystemType() : rowContext.systemType(),
            rowContext.systemIdentityKey() == null
                ? effectiveActiveContext.activeSystemIdentityKey()
                : rowContext.systemIdentityKey()
        );
    }

    String canonicalFacilityName(String rawFacility, String rawBuilding) {
        Map<String, String> identityValues = new LinkedHashMap<>();
        if (rawFacility != null && !rawFacility.isBlank()) {
            identityValues.put("legacy-1", rawFacility);
        }
        if (rawBuilding != null && !rawBuilding.isBlank()) {
            identityValues.put("legacy-2", rawBuilding);
        }
        return resolveFacilityName(resolveSublocation(identityValues, null), rawFacility);
    }

    String canonicalSystemTypeName(String rawSystem) {
        SystemTypeConfig systemType = resolveSystemType(rawSystem, null);
        return systemType == null ? null : resolveSystemTypeName(systemType, rawSystem);
    }

    private SublocationConfig resolveSublocation(
        Map<String, String> identityValues,
        SublocationConfig previousSublocation
    ) {
        for (String value : identityValues.values()) {
            for (SublocationConfig sublocation : config.sublocations()) {
                if (matchesAny(value, sublocation.buildingAliases())) {
                    return sublocation;
                }
            }
        }
        for (String value : identityValues.values()) {
            String normalizedValue = normalizeKey(value);
            if (normalizedValue == null) {
                continue;
            }
            for (SublocationConfig sublocation : config.sublocations()) {
                if (Objects.equals(normalizedValue, normalizeKey(sublocation.key()))
                    || Objects.equals(normalizedValue, normalizeKey(sublocation.displayName()))) {
                    return sublocation;
                }
            }
            List<SublocationConfig> facilityMatches = sublocationsByFacilityAlias.getOrDefault(
                normalizedValue,
                List.of()
            );
            if (facilityMatches.size() == 1) {
                return facilityMatches.getFirst();
            }
            SublocationConfig defaultMatch = defaultSublocationsByFacilityAlias.get(normalizedValue);
            if (defaultMatch != null) {
                return defaultMatch;
            }
        }
        return previousSublocation;
    }

    private SystemResolution resolveSystemType(
        Map<String, String> rowIdentityValues,
        ActiveImportContext activeContext
    ) {
        for (Map.Entry<String, String> identity : rowIdentityValues.entrySet()) {
            SystemTypeConfig resolved = resolveSystemType(identity.getValue(), null);
            if (resolved != null) {
                return new SystemResolution(resolved, identity.getKey());
            }
        }
        if (activeContext.activeSystemIdentityKey() != null
            && rowIdentityValues.containsKey(activeContext.activeSystemIdentityKey())) {
            return new SystemResolution(null, activeContext.activeSystemIdentityKey());
        }
        return new SystemResolution(
            activeContext.activeSystemType(),
            activeContext.activeSystemIdentityKey()
        );
    }

    private Map<String, String> mergeIdentityValues(
        Map<String, String> activeValues,
        Map<String, String> rowValues
    ) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (activeValues != null) {
            merged.putAll(activeValues);
        }
        if (rowValues != null) {
            rowValues.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    merged.put(key, value.strip());
                }
            });
        }
        return LocationDashboardIdentitySupport.immutableCopy(merged);
    }

    private String firstIdentityValue(Map<String, String> identityValues, String excludedIdentityKey) {
        if (identityValues == null) {
            return null;
        }
        return identityValues.entrySet().stream()
            .filter(entry -> !Objects.equals(entry.getKey(), excludedIdentityKey))
            .map(Map.Entry::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
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
        Map<String, String> activeIdentityValues,
        SublocationConfig activeSublocation,
        SystemTypeConfig activeSystemType,
        String activeSystemIdentityKey
    ) {
        ActiveImportContext {
            activeIdentityValues = LocationDashboardIdentitySupport.immutableCopy(activeIdentityValues);
        }
    }

    record RowImportContext(
        Map<String, String> identityValues,
        SublocationConfig sublocation,
        SystemTypeConfig systemType,
        String systemIdentityKey,
        String facilityName
    ) {
        RowImportContext {
            identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
        }
    }

    record SystemTypeAliasGroup(
        SystemTypeConfig representativeSystemType
    ) {
    }

    private record SystemResolution(SystemTypeConfig systemType, String identityKey) {
    }
}
