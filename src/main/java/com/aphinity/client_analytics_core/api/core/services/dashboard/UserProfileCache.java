package com.aphinity.client_analytics_core.api.core.services.dashboard;

import com.aphinity.client_analytics_core.api.core.response.dashboard.ProfileResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Short-lived cache for the small, non-sensitive profile response.
 * Passwords and JPA entities are intentionally never cached.
 */
@Service
public class UserProfileCache {
    public static final int MAX_ENTRIES = 512;
    public static final Duration TTL = Duration.ofMinutes(5);

    private final Cache<Long, ProfileResponse> cache;

    public UserProfileCache() {
        this(MAX_ENTRIES, TTL, Ticker.systemTicker());
    }

    UserProfileCache(int maximumSize, Duration ttl, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(ttl)
            .recordStats()
            .ticker(ticker)
            .build();
    }

    public ProfileResponse get(Long userId) {
        return userId == null ? null : cache.getIfPresent(userId);
    }

    public void put(Long userId, ProfileResponse profile) {
        if (userId != null && profile != null) {
            cache.put(userId, profile);
        }
    }

    public void invalidate(Long userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }

    long entryCount() {
        return cache.estimatedSize();
    }
}
