package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLocationDashboardNonConformanceIncidentCollectorTest {
    private final DefaultLocationDashboardNonConformanceIncidentCollector collector =
        new DefaultLocationDashboardNonConformanceIncidentCollector();

    @Test
    void deduplicatesWorksheetSurrogatesByCanonicalIncidentIdentity() {
        Map<String, String> identity = Map.of(
            "facility", "Hoag Hospital Newport Beach",
            "system", "Utility Domestic Hot",
            "pointOfUse", "Sink 1"
        );

        List<LocationDashboardNonConformanceIncident> incidents = collector.collect(List.of(
            failure(identity, "worksheet-sample|row|hpc|2026-06-01|F5"),
            failure(identity, "worksheet-sample|row|hpc|2026-06-01|G5")
        ));

        assertEquals(1, incidents.size());
        assertEquals("Hoag Hospital Newport Beach", incidents.getFirst().facilityName());
        assertEquals("Utility Domestic Hot", incidents.getFirst().systemTypeName());
        assertEquals("HPC", incidents.getFirst().measurementName());
    }

    @Test
    void preservesDistinctSameDayFailuresWhenNoSampleIdentityExists() {
        Map<String, String> identity = Map.of(
            "facility", "Hoag Hospital Newport Beach",
            "system", "Utility Domestic Hot",
            "pointOfUse", "Sink 1"
        );

        assertEquals(2, collector.collect(List.of(
            failure(identity, null),
            failure(identity, null)
        )).size());
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint failure(
        Map<String, String> identity,
        String sampleIdentity
    ) {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            LocalDate.parse("2026-06-01"),
            "Hoag Hospital Newport Beach",
            "Utility Domestic Hot",
            "HPC",
            identity,
            "12",
            "CFU.mL",
            sampleIdentity,
            false,
            false,
            null,
            LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
        );
    }
}
