package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationDashboardSample;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardSamplePersistenceServiceTest {
    private final LocationDashboardSamplePersistenceService service = new LocationDashboardSamplePersistenceService(null);

    @Test
    void persistsAnalyzedWorksheetSamplesWithoutSpreadsheetSampleIdentity() {
        Location location = new Location();
        location.setId(10L);

        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> analyzedSamples = List.of(
            analyzedSample("HPC", null, true, LocationDashboardImportStrategy.SampleOrigin.WORKSHEET),
            analyzedSample("Endotoxin", null, true, LocationDashboardImportStrategy.SampleOrigin.WORKSHEET),
            analyzedSample("Legionella", "comment-sample-1", false, LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
        );

        List<LocationDashboardSample> persistedSamples = service.toPersistedSamples(location, analyzedSamples);

        assertEquals(3, persistedSamples.size());
        assertEquals(3, persistedSamples.stream().map(LocationDashboardSample::getSampleIdentity).distinct().count());
        assertTrue(persistedSamples.stream().allMatch(sample -> sample.getSampleIdentity() != null));

        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> rehydratedSamples = persistedSamples.stream()
            .map(service::toAnalyzedSamplePoint)
            .toList();

        assertEquals(3, rehydratedSamples.size());
        assertEquals(2, rehydratedSamples.stream().filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET).count());
        assertTrue(rehydratedSamples.stream()
            .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET)
            .allMatch(sample -> sample.sampleIdentity() == null));
        assertEquals("comment-sample-1", rehydratedSamples.stream()
            .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
            .findFirst()
            .orElseThrow()
            .sampleIdentity());
    }

    @Test
    void rehydratesGeneratedIdentityAsNullForAnalysisMatching() {
        LocationDashboardSample sample = new LocationDashboardSample();
        sample.setObservedDate(LocalDate.parse("2025-01-01"));
        sample.setFacilityName("Irvine");
        sample.setSystemName("Critical SPD");
        sample.setMeasurementName("HPC");
        sample.setSampleIdentity("__generated__|2025-01-01|irvine||||hpc|||WORKSHEET|1");
        sample.setCompliant(false);
        sample.setOrigin(LocationDashboardImportStrategy.SampleOrigin.WORKSHEET.name());

        LocationDashboardImportStrategy.AnalyzedSamplePoint rehydratedSample = service.toAnalyzedSamplePoint(sample);

        assertNull(rehydratedSample.sampleIdentity());
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample(
        String measurementName,
        String sampleIdentity,
        boolean compliant,
        LocationDashboardImportStrategy.SampleOrigin origin
    ) {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            LocalDate.parse("2025-01-01"),
            "Irvine",
            "Irvine",
            "Critical SPD",
            "Critical SPD",
            measurementName,
            "POU 1",
            "Range",
            sampleIdentity,
            compliant,
            false,
            null,
            origin
        );
    }
}
