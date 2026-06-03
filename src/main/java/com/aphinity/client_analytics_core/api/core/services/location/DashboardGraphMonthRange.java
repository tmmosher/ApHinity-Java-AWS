package com.aphinity.client_analytics_core.api.core.services.location;

import java.time.LocalDate;

public record DashboardGraphMonthRange(Integer months) {
    public static final int ALL_TIME_REQUEST_VALUE = -1;
    public static final DashboardGraphMonthRange ALL_TIME = new DashboardGraphMonthRange(null);

    public DashboardGraphMonthRange {
        if (months != null && months <= 0) {
            months = null;
        }
    }

    public static DashboardGraphMonthRange fromRequestValue(Integer monthRange) {
        if (monthRange == null || monthRange <= 0) {
            return ALL_TIME;
        }
        return new DashboardGraphMonthRange(monthRange);
    }

    public boolean isAllTime() {
        return months == null;
    }

    public LocalDate windowStartInclusive(LocalDate anchorDate) {
        if (isAllTime() || anchorDate == null) {
            return null;
        }
        return anchorDate.minusMonths(months).withDayOfMonth(1);
    }
}
