package com.aphinity.client_analytics_core.api.core.entities.servicecalendar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ServiceEventResponsibility {
    CLIENT("client"),
    PARTNER("partner");

    private final String databaseValue;

    ServiceEventResponsibility(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String getDatabaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static ServiceEventResponsibility fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(responsibility ->
                responsibility.databaseValue.equalsIgnoreCase(value)
                    || responsibility.name().equalsIgnoreCase(value)
            )
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown service event responsibility: " + value));
    }
}
