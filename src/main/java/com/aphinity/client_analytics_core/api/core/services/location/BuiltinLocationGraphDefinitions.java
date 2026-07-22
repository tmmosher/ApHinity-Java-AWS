package com.aphinity.client_analytics_core.api.core.services.location;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Spring composition for the built-in graph modules. */
@Configuration
public class BuiltinLocationGraphDefinitions {
    @Bean
    LocationGraphDefinition pieGraphDefinition() {
        return definition("pie", "pie", Set.of(), LocationGraphTemplateFactory::pieTemplate);
    }

    @Bean
    LocationGraphDefinition indicatorGraphDefinition() {
        return definition("indicator", "indicator", Set.of(), LocationGraphTemplateFactory::indicatorTemplate);
    }

    @Bean
    LocationGraphDefinition barGraphDefinition() {
        return definition("bar", "bar", Set.of(), LocationGraphTemplateFactory::barTemplate);
    }

    @Bean
    LocationGraphDefinition scatterGraphDefinition() {
        return definition("scatter", "scatter", Set.of("line"), LocationGraphTemplateFactory::scatterTemplate);
    }

    @Bean
    LocationGraphDefinition tableGraphDefinition() {
        return definition("table", "table", Set.of(), LocationGraphTemplateFactory::tableTemplate);
    }

    @Bean
    LocationGraphDefinition sunburstGraphDefinition() {
        return definition("sunburst", "sunburst", Set.of(), LocationGraphTemplateFactory::sunburstTemplate);
    }

    public static Set<LocationGraphDefinition> defaults() {
        BuiltinLocationGraphDefinitions definitions = new BuiltinLocationGraphDefinitions();
        return Set.of(
            definitions.pieGraphDefinition(),
            definitions.indicatorGraphDefinition(),
            definitions.barGraphDefinition(),
            definitions.scatterGraphDefinition(),
            definitions.tableGraphDefinition(),
            definitions.sunburstGraphDefinition()
        );
    }

    private static LocationGraphDefinition definition(
        String key,
        String traceType,
        Set<String> aliases,
        java.util.function.Function<String, LocationGraphTemplateFactory.GraphTemplate> creator
    ) {
        return new SimpleLocationGraphDefinition(key, traceType, aliases, creator);
    }
}
