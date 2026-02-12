package com.aphinity.client_analytics_core.api.core.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum LocationInviteStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REVOKED("revoked"),
    EXPIRED("expired");

    private final String databaseValue;

    LocationInviteStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String getDatabaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static LocationInviteStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(status -> status.databaseValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown location invite status: " + value));
    }
}
