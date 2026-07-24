package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Immutable domain result describing when a nonconforming sample returned to
 * conformance.
 *
 * <p>The anchor is deliberately distinct from the sample's observed date:
 * comment-derived laboratory samples may start their turnaround clock when the
 * result was received.</p>
 */
public record ConformanceResolution(
    LocalDate anchorDate,
    LocalDate restoredDate
) {
    public ConformanceResolution {
        if (anchorDate == null) {
            throw new IllegalArgumentException("Conformance resolution anchor date is required");
        }
        if (restoredDate == null || !restoredDate.isAfter(anchorDate)) {
            throw new IllegalArgumentException("Conformance restoration must occur after its anchor date");
        }
    }

    public long turnaroundDays() {
        return ChronoUnit.DAYS.between(anchorDate, restoredDate);
    }
}
