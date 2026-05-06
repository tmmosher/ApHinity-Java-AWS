package com.aphinity.client_analytics_core.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyValidatorTest {
    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void rejectsPasswordsShorterThanTwelveCharacters() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            validator.validateOrThrow("Abcd123!xy")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Must be at least 12 characters", ex.getReason());
    }

    @Test
    void rejectsShortPasswordsBeforeCheckingForSpecialCharacters() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            validator.validateOrThrow("Abcdef12345")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Must be at least 12 characters", ex.getReason());
    }

    @Test
    void acceptsPasswordsAtLeastTwelveCharactersLong() {
        validator.validateOrThrow("Abcd123!xyzQ");
    }
}
