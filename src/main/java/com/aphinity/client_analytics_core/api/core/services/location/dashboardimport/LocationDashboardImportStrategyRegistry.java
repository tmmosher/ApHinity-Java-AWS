package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads configured dashboard import strategies from classpath JSON resources and
 * resolves them by normalized location name.
 */
@Service
public class LocationDashboardImportStrategyRegistry {
    private static final String RESOURCE_PATTERN = "classpath*:location-dashboard-import/*.json";
    private static final ObjectMapper STRATEGY_CONFIG_OBJECT_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();

    private final Map<String, LocationDashboardImportStrategy> strategiesByLocationName;

    public LocationDashboardImportStrategyRegistry() {
        this.strategiesByLocationName = loadStrategies();
    }

    /**
     * Finds the configured strategy for a location.
     *
     * @param locationName location name from the persisted location record
     * @return matching strategy, if one is configured
     */
    public Optional<LocationDashboardImportStrategy> resolve(String locationName) {
        return Optional.ofNullable(strategiesByLocationName.get(normalizeKey(locationName)));
    }

    private Map<String, LocationDashboardImportStrategy> loadStrategies() {
        Map<String, LocationDashboardImportStrategy> strategies = new LinkedHashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(RESOURCE_PATTERN);
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    LocationDashboardImportStrategyConfig config =
                        LocationDashboardImportStrategyRegistry.STRATEGY_CONFIG_OBJECT_MAPPER.readValue(inputStream, LocationDashboardImportStrategyConfig.class);
                    ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(config);
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
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load dashboard import strategies", ex);
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
