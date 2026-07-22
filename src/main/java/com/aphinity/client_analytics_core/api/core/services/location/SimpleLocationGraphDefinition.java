package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdateTraceValidator;
import java.util.Set;
import java.util.function.Function;

/** Declarative graph definition used by built-in and feature-specific modules. */
public record SimpleLocationGraphDefinition(
    String key,
    String traceType,
    Set<String> aliases,
    Function<String, LocationGraphTemplateFactory.GraphTemplate> templateCreator,
    LocationGraphUpdateTraceValidator validator
) implements LocationGraphDefinition {
    public SimpleLocationGraphDefinition {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    public SimpleLocationGraphDefinition(
        String key,
        String traceType,
        Set<String> aliases,
        Function<String, LocationGraphTemplateFactory.GraphTemplate> templateCreator
    ) {
        this(key, traceType, aliases, templateCreator, null);
    }

    @Override
    public LocationGraphTemplateFactory.GraphTemplate createTemplate(String locationName) {
        return templateCreator.apply(locationName);
    }
}
