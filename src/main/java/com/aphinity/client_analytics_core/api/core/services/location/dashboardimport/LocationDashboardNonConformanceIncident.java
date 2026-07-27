package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.Map;

/**
 * Canonical, location-independent non-conformance incident consumed by
 * dashboard metrics and projections.
 */
record LocationDashboardNonConformanceIncident(
    LocalDate observedDate,
    String facilityName,
    String measurementName,
    Map<String, String> identityValues,
    String sampleIdentity,
    boolean resolved,
    Long turnaroundDays,
    String systemTypeName
) {
    LocationDashboardNonConformanceIncident {
        identityValues = LocationDashboardIdentitySupport.immutableCopy(identityValues);
    }

    LocationDashboardNonConformanceIncident(
        LocalDate observedDate,
        String facilityName,
        String measurementName,
        Map<String, String> identityValues,
        String sampleIdentity,
        boolean resolved,
        Long turnaroundDays
    ) {
        this(
            observedDate,
            facilityName,
            measurementName,
            identityValues,
            sampleIdentity,
            resolved,
            turnaroundDays,
            null
        );
    }

    static LocationDashboardNonConformanceIncident merge(
        LocationDashboardNonConformanceIncident persisted,
        LocationDashboardNonConformanceIncident analyzed
    ) {
        if (persisted == null) {
            return analyzed;
        }
        if (analyzed == null) {
            return persisted;
        }
        boolean resolved = persisted.resolved || analyzed.resolved;
        Long turnaroundDays = analyzed.turnaroundDays != null
            ? analyzed.turnaroundDays
            : persisted.turnaroundDays;
        return new LocationDashboardNonConformanceIncident(
            analyzed.observedDate != null ? analyzed.observedDate : persisted.observedDate,
            analyzed.facilityName != null ? analyzed.facilityName : persisted.facilityName,
            analyzed.measurementName != null ? analyzed.measurementName : persisted.measurementName,
            !analyzed.identityValues.isEmpty() ? analyzed.identityValues : persisted.identityValues,
            analyzed.sampleIdentity != null ? analyzed.sampleIdentity : persisted.sampleIdentity,
            resolved,
            turnaroundDays,
            analyzed.systemTypeName != null ? analyzed.systemTypeName : persisted.systemTypeName
        );
    }
}
