package com.aphinity.client_analytics_core.api.core.services.location;

import java.time.LocalDate;

/**
 * Represents the requested dashboard graph time window.
 *
 * <p>A null month count is the canonical all-time value, while positive month
 * counts are interpreted as rolling windows anchored to the first day of the
 * month containing the supplied date.</p>
 *
 * @param months number of months to include, or null for all-time
 */
public record DashboardGraphMonthRange(Integer months) {
    public static final int ALL_TIME_REQUEST_VALUE = -1;
    public static final DashboardGraphMonthRange ALL_TIME = new DashboardGraphMonthRange(null);

    public DashboardGraphMonthRange {
        if (months != null && months <= 0) {
            months = null;
        }
    }

    /**
     * Converts the HTTP request value into the internal range representation.
     *
     * @param monthRange requested month range
     * @return finite range for positive values, otherwise all-time
     */
    public static DashboardGraphMonthRange fromRequestValue(Integer monthRange) {
        if (monthRange == null || monthRange <= 0) {
            return ALL_TIME;
        }
        return new DashboardGraphMonthRange(monthRange);
    }

    public boolean isAllTime() {
        return months == null;
    }

    /**
     * Resolves the inclusive first day of the expanded data window used by
     * time-series renderers. One month before the selected range is included
     * so Plotly trendlines have enough context to enter the displayed window
     * cleanly.
     */
    public LocalDate dataWindowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return monthAnchor(anchorDate).minusMonths((long) months + 1L);
    }

    public LocalDate displayWindowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return selectedWindowStartInclusive(anchorDate).minusDays(5);
    }

    /**
     * Resolves the inclusive first day of the selected reporting window. This
     * boundary is suitable for aggregate and derived graph inputs that must not
     * include renderer-only context from the preceding month.
     */
    public LocalDate selectedWindowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return monthAnchor(anchorDate).minusMonths(months);
    }

    public LocalDate displayWindowEndInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return anchorDate;
    }

    private LocalDate monthAnchor(LocalDate anchorDate) {
        return anchorDate.withDayOfMonth(1);
    }
}
