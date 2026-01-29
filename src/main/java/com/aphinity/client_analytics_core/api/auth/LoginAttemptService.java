package com.aphinity.client_analytics_core.api.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * This service manages login attempts in-memory with a Caffeine cache.
 */
@Service
public class LoginAttemptService {
    private final LoginAttemptProperties properties;
    private final Cache<String, Integer> failures;

    public LoginAttemptService(LoginAttemptProperties properties) {
        this(properties, buildCache(properties));
    }

    public LoginAttemptService() {
        this(new LoginAttemptProperties());
    }

    LoginAttemptService(LoginAttemptProperties properties, Cache<String, Integer> failures) {
        this.properties = properties;
        this.failures = failures;
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        failures.asMap().merge(key, 1, Integer::sum);
    }

    public void recordSuccess(String email) {
        failures.invalidate(normalize(email));
    }

    public boolean isCaptchaRequired(String email) {
        Integer failures = this.failures.getIfPresent(normalize(email));
        return failures != null && failures >= properties.getMaxFailures();
    };

    private String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static Cache<String, Integer> buildCache(LoginAttemptProperties properties) {
        Duration window = properties.getFailureWindow();
        return Caffeine.newBuilder()
            .expireAfterWrite(window)
            .build();
    }
}
