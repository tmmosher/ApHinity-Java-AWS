package com.aphinity.client_analytics_core.logging;

import java.time.Instant;

/**
 * Immutable log queue entry.
 *
 * @param timestamp entry timestamp
 * @param message entry message
 */
public record LogEntry(
    Instant timestamp,
    String message
) {
}
