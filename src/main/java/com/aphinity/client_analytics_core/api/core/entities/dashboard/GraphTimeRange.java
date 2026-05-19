package com.aphinity.client_analytics_core.api.core.entities.dashboard;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

public enum GraphTimeRange {
    ONE_MONTH("one_month", "oneMonth", 1),
    THREE_MONTHS("three_months", "threeMonths", 3),
    ALL_TIME("all_time", "allTime", null);

    private final String databaseValue;
    private final String responseKey;
    private final Integer lookbackMonths;

    GraphTimeRange(String databaseValue, String responseKey, Integer lookbackMonths) {
        this.databaseValue = databaseValue;
        this.responseKey = responseKey;
        this.lookbackMonths = lookbackMonths;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public String getResponseKey() {
        return responseKey;
    }

    public Integer getLookbackMonths() {
        return lookbackMonths;
    }

    public boolean isRollingWindow() {
        return lookbackMonths != null;
    }

    public LocalDate windowStartInclusive(LocalDate anchorDate) {
        if (!isRollingWindow() || anchorDate == null) {
            return null;
        }
        return anchorDate.minusMonths(lookbackMonths);
    }

    public static GraphTimeRange fromDatabaseValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ALL_TIME;
        }
        String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(candidate -> candidate.databaseValue.equals(normalized) || candidate.name().equalsIgnoreCase(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown graph time range: " + rawValue));
    }
}
