package com.aphinity.client_analytics_core.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
    String code,
    String message,
    int status,
    Instant timestamp,
    Map<String, String> fieldErrors
) {
}
