package com.aphinity.client_analytics_core.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error payload returned to clients.
 *
 * @param code machine-readable error identifier
 * @param message human-readable error message safe for clients
 * @param status HTTP status code
 * @param timestamp server timestamp when the error response was created
 * @param fieldErrors optional field-level validation errors keyed by field name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
    String code,
    String message,
    int status,
    Instant timestamp,
    Map<String, String> fieldErrors
) {
}
