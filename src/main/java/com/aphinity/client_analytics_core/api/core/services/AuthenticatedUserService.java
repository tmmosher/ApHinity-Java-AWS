package com.aphinity.client_analytics_core.api.core.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves and validates authenticated user identity from JWT principals.
 */
@Service
public class AuthenticatedUserService {
    /**
     * Extracts user id from the JWT subject claim.
     *
     * @param jwt authenticated JWT principal
     * @return parsed user id
     */
    public Long resolveAuthenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw invalidAuthenticatedUser();
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw invalidAuthenticatedUser();
        }
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }
}
