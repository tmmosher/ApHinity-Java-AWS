package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for validating trace data for a graph payload family.
 */
public interface LocationGraphUpdateTraceValidator {
    /**
     * Indicates whether this validator handles the supplied graph family.
     *
     * @param family graph payload family
     * @return true when this validator should validate the payload
     */
    boolean supports(GraphPayloadFamily family);

    /**
     * Validates traces for a canonical Plotly trace type.
     *
     * @param traces trace payloads to validate
     * @param canonicalTraceType normalized trace type expected for every trace
     */
    void validate(List<Map<String, Object>> traces, String canonicalTraceType);
}
