package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.services.location.payload.LocationGraphUpdateTraceValidator;
import java.util.Set;

/**
 * Pluggable graph creation module. Definition keys identify graph semantics,
 * while trace types identify only the Plotly rendering family.
 */
public interface LocationGraphDefinition {
    String key();

    String traceType();

    default Set<String> aliases() {
        return Set.of();
    }

    /** Optional semantic validator for definitions sharing a Plotly trace family. */
    default LocationGraphUpdateTraceValidator validator() {
        return null;
    }

    LocationGraphTemplateFactory.GraphTemplate createTemplate(String locationName);
}
