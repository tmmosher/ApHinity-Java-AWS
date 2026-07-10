package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocationDashboardCacheTest {
    @Test
    void graphProjectionsAreScopedByLocationGraphAndRange() {
        LocationDashboardCache cache = new LocationDashboardCache(
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            1024 * 1024,
            1024 * 1024,
            Ticker.systemTicker()
        );
        LocationDashboardTimeRangeService.MonthRangeGraphProjection firstProjection =
            new LocationDashboardTimeRangeService.MonthRangeGraphProjection(
                List.of(Map.of("name", "first")),
                Map.of()
            );
        LocationDashboardTimeRangeService.MonthRangeGraphProjection secondProjection =
            new LocationDashboardTimeRangeService.MonthRangeGraphProjection(
                List.of(Map.of("name", "second")),
                Map.of()
            );
        LocationDashboardCache.GraphProjectionCacheKey firstKey = new LocationDashboardCache.GraphProjectionCacheKey(
            1L,
            10L,
            3,
            LocalDate.of(2026, 7, 6),
            Instant.ofEpochSecond(1),
            Instant.ofEpochSecond(2)
        );
        LocationDashboardCache.GraphProjectionCacheKey secondKey = new LocationDashboardCache.GraphProjectionCacheKey(
            2L,
            10L,
            3,
            LocalDate.of(2026, 7, 6),
            Instant.ofEpochSecond(1),
            Instant.ofEpochSecond(2)
        );

        cache.putGraphProjection(firstKey, firstProjection);
        cache.putGraphProjection(secondKey, secondProjection);

        assertEquals(firstProjection, cache.getGraphProjection(firstKey));
        assertEquals(secondProjection, cache.getGraphProjection(secondKey));

        cache.invalidateLocation(1L);

        assertNull(cache.getGraphProjection(firstKey));
        assertEquals(secondProjection, cache.getGraphProjection(secondKey));
    }

    @Test
    void historicalDataExpiresAfterWrite() {
        AtomicLong tickerNanos = new AtomicLong();
        LocationDashboardCache cache = new LocationDashboardCache(
            Duration.ofSeconds(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            1024 * 1024,
            1024 * 1024,
            tickerNanos::get
        );
        LocationDashboardCache.HistoricalDataCacheKey key =
            new LocationDashboardCache.HistoricalDataCacheKey(1L, Instant.ofEpochSecond(1));
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(Map.of(), List.of(), List.of());

        cache.putHistoricalData(key, historicalData);
        assertEquals(historicalData, cache.getHistoricalData(key));

        tickerNanos.addAndGet(Duration.ofSeconds(6).toNanos());

        assertNull(cache.getHistoricalData(key));
    }
}
