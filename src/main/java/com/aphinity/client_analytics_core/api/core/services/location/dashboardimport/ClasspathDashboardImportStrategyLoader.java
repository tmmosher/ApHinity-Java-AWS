package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Infrastructure adapter that discovers configured import modules on the classpath. */
@Component
public class ClasspathDashboardImportStrategyLoader {
    private static final String RESOURCE_PATTERN = "classpath*:location-dashboard-import/*.json";

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Autowired
    public ClasspathDashboardImportStrategyLoader(
        @Qualifier("dashboardImportObjectMapper") ObjectMapper objectMapper
    ) {
        this(objectMapper, new PathMatchingResourcePatternResolver());
    }

    ClasspathDashboardImportStrategyLoader(
        ObjectMapper objectMapper,
        PathMatchingResourcePatternResolver resourceResolver
    ) {
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
    }

    public List<LocationDashboardImportStrategy> load() {
        List<LocationDashboardImportStrategy> strategies = new ArrayList<>();
        try {
            for (Resource resource : resourceResolver.getResources(RESOURCE_PATTERN)) {
                try (InputStream inputStream = resource.getInputStream()) {
                    LocationDashboardImportStrategyConfig config = objectMapper.readValue(
                        inputStream,
                        LocationDashboardImportStrategyConfig.class
                    );
                    strategies.add(new ConfiguredLocationDashboardImportStrategy(config));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load dashboard import strategies", ex);
        }
        return List.copyOf(strategies);
    }

}
