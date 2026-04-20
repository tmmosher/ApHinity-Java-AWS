package com.aphinity.client_analytics_core.api.core.services.location.payload;

import java.util.List;
import java.util.Map;

public interface LocationGraphUpdateTraceValidator {
    boolean supports(GraphPayloadFamily family);

    void validate(List<Map<String, Object>> traces, String canonicalTraceType);
}
