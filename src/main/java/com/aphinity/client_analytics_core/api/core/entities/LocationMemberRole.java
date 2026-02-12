package com.aphinity.client_analytics_core.api.core.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum LocationMemberRole {
    PARTNER("partner"),
    ADMIN("admin"),
    CLIENT("client");

    private final String databaseValue;

    LocationMemberRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String getDatabaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static LocationMemberRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(role -> role.databaseValue.equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown location member role: " + value));
    }
}
