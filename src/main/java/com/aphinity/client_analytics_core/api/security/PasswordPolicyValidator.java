package com.aphinity.client_analytics_core.api.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enforces password requirements for user-managed password flows.
 */
@Component
public class PasswordPolicyValidator {
    private static final Pattern LEN_8_PLUS = Pattern.compile(".{8,}");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-={};':\"\\\\|,.<>/?`~]");

    private final Map<Pattern, String> passwordRequirements = buildPasswordRequirements();

    /**
     * Validates password strength and throws a standardized bad-request error on failure.
     *
     * @param password raw password value
     */
    public void validateOrThrow(String password) {
        String value = password == null ? "" : password;
        for (Map.Entry<Pattern, String> requirement : passwordRequirements.entrySet()) {
            if (!requirement.getKey().matcher(value).find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, requirement.getValue());
            }
        }
    }

    /**
     * Builds ordered password requirement checks and their error messages.
     *
     * @return immutable map of validation patterns to user-facing messages
     */
    private Map<Pattern, String> buildPasswordRequirements() {
        // LinkedHashMap preserves check order so clients get deterministic first-failure messages.
        Map<Pattern, String> requirements = new LinkedHashMap<>();
        requirements.put(LEN_8_PLUS, "Must be at least 8 characters");
        requirements.put(HAS_DIGIT, "Must contain at least one digit");
        requirements.put(HAS_LETTER, "Must contain at least one letter");
        requirements.put(HAS_SPECIAL, "Must contain at least one special character");
        return Map.copyOf(requirements);
    }
}
