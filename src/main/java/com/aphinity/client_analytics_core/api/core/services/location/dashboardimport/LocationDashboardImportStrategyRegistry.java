package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads configured dashboard import strategies from classpath JSON resources and
 * resolves them by normalized location name.
 */
@Service
public class LocationDashboardImportStrategyRegistry implements DashboardImportStrategyResolver {
    private final Map<String, LocationDashboardImportStrategy> strategiesByLocationName;

    @Autowired
    public LocationDashboardImportStrategyRegistry(ClasspathDashboardImportStrategyLoader loader) {
        this.strategiesByLocationName = indexStrategies(loader.load());
    }

    /**
     * Finds the configured strategy for a location.
     *
     * @param locationName location name from the persisted location record
     * @return matching strategy, if one is configured
     */
    @Override
    public Optional<LocationDashboardImportStrategy> resolve(String locationName) {
        return Optional.ofNullable(strategiesByLocationName.get(normalizeKey(locationName)));
    }

    private Map<String, LocationDashboardImportStrategy> indexStrategies(
        Iterable<LocationDashboardImportStrategy> loadedStrategies
    ) {
        Map<String, LocationDashboardImportStrategy> strategies = new LinkedHashMap<>();
        for (LocationDashboardImportStrategy strategy : loadedStrategies) {
            String normalizedLocationName = normalizeKey(strategy.locationName());
            if (normalizedLocationName == null) {
                throw new IllegalStateException("Dashboard import strategy location name is required");
            }
            if (strategies.putIfAbsent(normalizedLocationName, strategy) != null) {
                throw new IllegalStateException(
                    "Duplicate dashboard import strategy for location: " + strategy.locationName()
                );
            }
        }
        return Map.copyOf(strategies);
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
