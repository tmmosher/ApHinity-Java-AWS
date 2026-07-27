package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        DashboardGraphProjection firstProjection =
            new DashboardGraphProjection(
                List.of(Map.of("name", "first")),
                Map.of()
            );
        DashboardGraphProjection secondProjection =
            new DashboardGraphProjection(
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

        assertEquals(historicalData, cache.getOrComputeHistoricalData(key, () -> historicalData));
        assertEquals(historicalData, cache.getOrComputeHistoricalData(
            key,
            () -> { throw new AssertionError("unexpired historical data was recomputed"); }
        ));

        tickerNanos.addAndGet(Duration.ofSeconds(6).toNanos());

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData replacement =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(Map.of(), List.of(), List.of());
        assertEquals(replacement, cache.getOrComputeHistoricalData(key, () -> replacement));
    }

    @Test
    void historicalDataComputationIsSingleFlightForConcurrentRequests() throws Exception {
        LocationDashboardCache cache = new LocationDashboardCache();
        LocationDashboardCache.HistoricalDataCacheKey key =
            new LocationDashboardCache.HistoricalDataCacheKey(1L, Instant.ofEpochSecond(1));
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(Map.of(), List.of(), List.of());
        AtomicLong computations = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<LocationDashboardDerivedGraphSupport.HistoricalDerivedData>> futures =
                java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return cache.getOrComputeHistoricalData(key, () -> {
                            computations.incrementAndGet();
                            loaderStarted.countDown();
                            try {
                                releaseLoader.await();
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(ex);
                            }
                            return historicalData;
                        });
                    }))
                    .toList();

            start.countDown();
            loaderStarted.await();
            releaseLoader.countDown();
            for (Future<LocationDashboardDerivedGraphSupport.HistoricalDerivedData> future : futures) {
                assertEquals(historicalData, future.get());
            }
            assertEquals(1L, computations.get());
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }
}
