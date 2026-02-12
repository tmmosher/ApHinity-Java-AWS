package com.aphinity.client_analytics_core.api.core.response;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Canonical account-level roles exposed to clients.
 */
public enum AccountRole {
    ADMIN("admin"),
    PARTNER("partner"),
    CLIENT("client");

    private final String value;

    AccountRole(String value) {
        this.value = value;
    }

    /**
     * Returns serialized API value for this role.
     */
    @JsonValue
    public String getValue() {
        return value;
    }
}
