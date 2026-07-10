package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded cache for location dashboard derivation inputs and range projections.
 *
 * <p>The cache is deliberately kept behind this service so the dashboard
 * services do not depend on Caffeine's API. It can be replaced by a distributed
 * implementation when graph requests are served by more than one application
 * instance.</p>
 */
@Service
public class LocationDashboardCache {
    public static final long HISTORICAL_DATA_MAX_WEIGHT_BYTES = 24L * 1024L * 1024L;
    public static final long GRAPH_PROJECTION_MAX_WEIGHT_BYTES = 40L * 1024L * 1024L;
    public static final Duration HISTORICAL_DATA_TTL = Duration.ofMinutes(5);
    public static final Duration GRAPH_PROJECTION_IDLE_TTL = Duration.ofMinutes(10);
    public static final Duration GRAPH_PROJECTION_TTL = Duration.ofMinutes(30);

    private final Cache<HistoricalDataCacheKey, LocationDashboardDerivedGraphSupport.HistoricalDerivedData>
        historicalDataCache;
    private final Cache<GraphProjectionCacheKey, LocationDashboardTimeRangeService.MonthRangeGraphProjection>
        graphProjectionCache;

    public LocationDashboardCache() {
        this(
            HISTORICAL_DATA_TTL,
            GRAPH_PROJECTION_IDLE_TTL,
            GRAPH_PROJECTION_TTL,
            HISTORICAL_DATA_MAX_WEIGHT_BYTES,
            GRAPH_PROJECTION_MAX_WEIGHT_BYTES,
            Ticker.systemTicker()
        );
    }

    LocationDashboardCache(
        Duration historicalDataTtl,
        Duration graphProjectionIdleTtl,
        Duration graphProjectionTtl,
        long historicalDataMaximumWeight,
        long graphProjectionMaximumWeight,
        Ticker ticker
    ) {
        this.historicalDataCache = Caffeine.newBuilder()
            .maximumWeight(historicalDataMaximumWeight)
            .weigher((HistoricalDataCacheKey key, LocationDashboardDerivedGraphSupport.HistoricalDerivedData value) ->
                estimateWeight(value))
            .expireAfterWrite(historicalDataTtl)
            .recordStats()
            .ticker(ticker)
            .build();
        this.graphProjectionCache = Caffeine.newBuilder()
            .maximumWeight(graphProjectionMaximumWeight)
            .weigher((GraphProjectionCacheKey key, LocationDashboardTimeRangeService.MonthRangeGraphProjection value) ->
                estimateWeight(value))
            .expireAfterAccess(graphProjectionIdleTtl)
            .expireAfterWrite(graphProjectionTtl)
            .recordStats()
            .ticker(ticker)
            .build();
    }

    LocationDashboardDerivedGraphSupport.HistoricalDerivedData getHistoricalData(
        HistoricalDataCacheKey key
    ) {
        return key == null ? null : historicalDataCache.getIfPresent(key);
    }

    void putHistoricalData(
        HistoricalDataCacheKey key,
        LocationDashboardDerivedGraphSupport.HistoricalDerivedData value
    ) {
        if (key != null && value != null) {
            historicalDataCache.put(key, value);
        }
    }

    LocationDashboardTimeRangeService.MonthRangeGraphProjection getGraphProjection(
        GraphProjectionCacheKey key
    ) {
        return key == null ? null : graphProjectionCache.getIfPresent(key);
    }

    void putGraphProjection(
        GraphProjectionCacheKey key,
        LocationDashboardTimeRangeService.MonthRangeGraphProjection value
    ) {
        if (key != null && value != null) {
            graphProjectionCache.put(key, value);
        }
    }

    /**
     * Removes every dashboard cache entry belonging to a location.
     * Explicit invalidation is used in addition to revisioned keys so writes
     * cannot leave stale payloads resident until their TTL elapses.
     */
    public void invalidateLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        historicalDataCache.asMap().keySet().removeIf(key -> Objects.equals(key.locationId(), locationId));
        graphProjectionCache.asMap().keySet().removeIf(key -> Objects.equals(key.locationId(), locationId));
    }

    long historicalDataEntryCount() {
        return historicalDataCache.estimatedSize();
    }

    long graphProjectionEntryCount() {
        return graphProjectionCache.estimatedSize();
    }

    public record HistoricalDataCacheKey(
        Long locationId,
        Instant sourceRevision
    ) {
    }

    public record GraphProjectionCacheKey(
        Long locationId,
        Long graphId,
        Integer monthRange,
        LocalDate anchorDate,
        Instant locationRevision,
        Instant graphRevision
    ) {
    }

    private static int estimateWeight(Object value) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        long weight = estimateValue(value, visited);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, weight));
    }

    private static long estimateValue(Object value, Set<Object> visited) {
        if (value == null) {
            return 8L;
        }
        if (value instanceof String stringValue) {
            return 40L + (long) stringValue.length() * 2L;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return 24L;
        }
        if (!visited.add(value)) {
            return 0L;
        }
        if (value instanceof LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData) {
            return 48L
                + estimateValue(historicalData.samplesByDate(), visited)
                + estimateValue(historicalData.nonConformances(), visited)
                + estimateValue(historicalData.rawSamples(), visited);
        }
        if (value instanceof LocationDashboardTimeRangeService.MonthRangeGraphProjection projection) {
            return 48L
                + estimateValue(projection.data(), visited)
                + estimateValue(projection.layout(), visited);
        }
        if (value instanceof Map<?, ?> map) {
            long weight = 48L;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                weight += estimateValue(entry.getKey(), visited);
                weight += estimateValue(entry.getValue(), visited);
            }
            return weight;
        }
        if (value instanceof Collection<?> collection) {
            long weight = 32L;
            for (Object element : collection) {
                weight += estimateValue(element, visited);
            }
            return weight;
        }
        if (value.getClass().isArray()) {
            long weight = 32L;
            int length = Array.getLength(value);
            for (int index = 0; index < length; index += 1) {
                weight += estimateValue(Array.get(value, index), visited);
            }
            return weight;
        }
        return 32L;
    }
}
