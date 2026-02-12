package com.aphinity.client_analytics_core.api.auth.services;

import com.aphinity.client_analytics_core.api.auth.properties.LoginAttemptProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Tracks failed login attempts per normalized email within a rolling time window.
 * <p>
 * This data is intentionally in-memory and short-lived; it is used only to decide when to
 * require captcha for additional protection against credential stuffing.
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

    /**
     * Increments failure count for an email identity.
     *
     * @param email email address used in login attempt
     */
    public void recordFailure(String email) {
        String key = normalize(email);
        failures.asMap().merge(key, 1, Integer::sum);
    }

    /**
     * Clears failure count after a successful authentication.
     *
     * @param email email address that authenticated successfully
     */
    public void recordSuccess(String email) {
        failures.invalidate(normalize(email));
    }

    /**
     * Indicates whether captcha must be solved before another login attempt is processed.
     *
     * @param email email address used in login attempt
     * @return {@code true} when failures reached configured threshold
     */
    public boolean isCaptchaRequired(String email) {
        Integer failures = this.failures.getIfPresent(normalize(email));
        return failures != null && failures >= properties.getMaxFailures();
    }

    private String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static Cache<String, Integer> buildCache(LoginAttemptProperties properties) {
        Duration window = properties.getFailureWindow();
        // Expire counters automatically so users are not permanently stuck behind captcha.
        return Caffeine.newBuilder()
            .expireAfterWrite(window)
            .build();
    }
}
