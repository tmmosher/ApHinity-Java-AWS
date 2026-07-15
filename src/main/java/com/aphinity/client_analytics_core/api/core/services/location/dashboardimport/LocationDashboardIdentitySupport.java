package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared handling for ordered, configuration-defined spreadsheet identities.
 */
final class LocationDashboardIdentitySupport {
    private LocationDashboardIdentitySupport() {
    }

    static Map<String, String> immutableCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                copy.put(key.strip(), value.strip());
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    static String normalizedIdentity(Map<String, String> values) {
        return immutableCopy(values).entrySet().stream()
            .map(entry -> normalize(entry.getKey()) + "=" + normalize(entry.getValue()))
            .sorted()
            .collect(Collectors.joining("|"));
    }

    static String encode(Map<String, String> values) {
        return immutableCopy(values).entrySet().stream()
            .map(entry -> encodeToken(entry.getKey()) + "=" + encodeToken(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    static Map<String, String> decode(String encodedValues) {
        if (encodedValues == null || encodedValues.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : encodedValues.split("&", -1)) {
            int delimiter = pair.indexOf('=');
            if (delimiter <= 0) {
                continue;
            }
            String key = decodeToken(pair.substring(0, delimiter));
            String value = decodeToken(pair.substring(delimiter + 1));
            if (!key.isBlank() && !value.isBlank()) {
                values.put(key, value);
            }
        }
        return immutableCopy(values);
    }

    static String encodeValue(String value) {
        return value == null ? "" : encodeToken(value);
    }

    static String decodeValue(String value) {
        return value == null || value.isBlank() ? null : decodeToken(value);
    }

    private static String normalize(String value) {
        String normalized = LocationDashboardGraphMetadataSupport.normalizeKey(value);
        return normalized == null ? "" : normalized;
    }

    private static String encodeToken(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decodeToken(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }
}
