package com.aphinity.client_analytics_core.api.auth.services;

/** Application boundary for invalidating a user's active refresh sessions. */
public interface RefreshSessionRevoker {
    void revokeAllForUser(Long userId);
}
