package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationDashboardSample;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationDashboardSampleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void persistsCorrectiveActionFallbackSampleIdentityAsGeneratedForHistoricalMatching() {
        LocationDashboardSampleRepository repository = mock(LocationDashboardSampleRepository.class);
        LocationDashboardSamplePersistenceService persistenceService = new LocationDashboardSamplePersistenceService(repository);
        Location location = new Location();
        location.setId(10L);
        ServiceEvent correctiveAction = new ServiceEvent();
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setEventDate(LocalDate.parse("2025-01-01"));
        correctiveAction.setEndEventDate(LocalDate.parse("2025-01-03"));
        correctiveAction.setStatus(ServiceEventStatus.COMPLETED);
        correctiveAction.setDescription(String.join("\n", List.of(
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine("HPC"),
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-01-01")),
            LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Irvine"),
            LocationDashboardCorrectiveActionMetadataSupport.systemLine("Critical SPD")
        )));
        LocationDashboardImportStrategy.LocationDashboardImportComputation computation =
            new LocationDashboardImportStrategy.LocationDashboardImportComputation(
                List.of(),
                List.of(),
                List.of(),
                List.of(analyzedSample("Endotoxin", null, true, LocationDashboardImportStrategy.SampleOrigin.WORKSHEET))
            );

        persistenceService.replaceLocationSamples(location, computation, List.of(correctiveAction));

        ArgumentCaptor<Iterable> samplesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(samplesCaptor.capture());
        List<LocationDashboardSample> persistedSamples = StreamSupport
            .stream(((Iterable<LocationDashboardSample>) samplesCaptor.getValue()).spliterator(), false)
            .toList();
        LocationDashboardSample correctiveActionSample = persistedSamples.stream()
            .filter(sample -> LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT.name().equals(sample.getOrigin()))
            .findFirst()
            .orElseThrow();

        assertTrue(correctiveActionSample.getSampleIdentity()
            .startsWith(LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX));
        LocationDashboardImportStrategy.AnalyzedSamplePoint rehydratedSample =
            persistenceService.toAnalyzedSamplePoint(correctiveActionSample);
        assertNull(rehydratedSample.sampleIdentity());
        assertEquals(
            LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                "HPC",
                LocalDate.parse("2025-01-01"),
                "Irvine",
                null,
                "Critical SPD",
                null,
                null,
                null
            ),
            LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                rehydratedSample.measurementName(),
                rehydratedSample.observedDate(),
                rehydratedSample.facilityName(),
                rehydratedSample.buildingName(),
                rehydratedSample.systemName(),
                rehydratedSample.pointOfUse(),
                rehydratedSample.basis(),
                rehydratedSample.sampleIdentity()
            )
        );
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
