package com.aphinity.client_analytics_core.api.core.services.dashboard;

import com.aphinity.client_analytics_core.api.core.response.dashboard.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.dashboard.ProfileResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserProfileCacheTest {
    @Test
    void profileEntriesExpireAndCanBeInvalidated() {
        AtomicLong tickerNanos = new AtomicLong();
        UserProfileCache cache = new UserProfileCache(2, Duration.ofSeconds(5), tickerNanos::get);
        ProfileResponse profile = new ProfileResponse("Jane", "jane@example.com", true, AccountRole.CLIENT);

        cache.put(7L, profile);
        assertEquals(profile, cache.get(7L));

        cache.invalidate(7L);
        assertNull(cache.get(7L));

        cache.put(7L, profile);
        tickerNanos.addAndGet(Duration.ofSeconds(6).toNanos());

        assertNull(cache.get(7L));
    }
}
