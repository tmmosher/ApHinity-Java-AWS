package com.aphinity.client_analytics_core.logging;

import java.time.Instant;

public record LogEntry(
    Instant timestamp,
    String message
) {
}
