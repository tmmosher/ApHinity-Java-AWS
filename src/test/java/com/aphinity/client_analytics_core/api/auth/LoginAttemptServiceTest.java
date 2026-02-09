package com.aphinity.client_analytics_core.api.auth;

import com.aphinity.client_analytics_core.api.auth.properties.LoginAttemptProperties;
import com.aphinity.client_analytics_core.api.auth.services.LoginAttemptService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {
    @Test
    void requiresCaptchaAfterMaxFailures() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(2);
        LoginAttemptService service = new LoginAttemptService(properties);

        assertFalse(service.isCaptchaRequired("User@Example.com"));

        service.recordFailure("User@Example.com");
        assertFalse(service.isCaptchaRequired("user@example.com"));

        service.recordFailure("user@example.com");
        assertTrue(service.isCaptchaRequired("user@example.com"));
    }

    @Test
    void recordSuccessClearsFailures() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(1);
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("user@example.com");
        assertTrue(service.isCaptchaRequired("user@example.com"));

        service.recordSuccess("user@example.com");
        assertFalse(service.isCaptchaRequired("user@example.com"));
    }

    @Test
    void nullEmailDoesNotBreakTracking() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(1);
        LoginAttemptService service = new LoginAttemptService(properties);

        assertFalse(service.isCaptchaRequired(null));

        service.recordFailure(null);
        assertTrue(service.isCaptchaRequired(null));

        service.recordSuccess(null);
        assertFalse(service.isCaptchaRequired(null));
    }
}
