package com.aphinity.client_analytics_core.api.core.services.location;

import java.time.LocalDate;

/**
 * Represents the requested dashboard graph time window.
 *
 * <p>A null month count is the canonical all-time value, while positive month
 * counts are interpreted as rolling windows anchored to a supplied date.</p>
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
     * Resolves the inclusive first day of the fetched data window. One month
     * before the visible range is included so Plotly trendlines have enough
     * context to enter the displayed window cleanly.
     *
     * @param anchorDate date used as the end of the requested window
     * @return first included month, or null for all-time ranges
     */
    public LocalDate windowStartInclusive(LocalDate anchorDate) {
        return dataWindowStartInclusive(anchorDate);
    }

    public LocalDate dataWindowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return displayWindowStartInclusive(anchorDate).minusMonths(1);
    }

    public LocalDate displayWindowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return anchorDate.minusMonths((long) months - 1L).withDayOfMonth(1);
    }
}
