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
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardIdentityFixtures.identityValues;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LocationDashboardSamplePersistenceServiceTest {
    private final LocationDashboardSamplePersistenceService service = new LocationDashboardSamplePersistenceService(null);

    @Test
    void persistsAndRehydratesArbitraryIdentityKeys() {
        Location location = new Location();
        location.setId(10L);
        Map<String, String> identities = new java.util.LinkedHashMap<>();
        identities.put("water train", "Cooling Towers");
        identities.put("sampling station", "City Water");
        identities.put("asset tag", "CT-12");
        LocationDashboardImportStrategy.AnalyzedSamplePoint sample =
            new LocationDashboardImportStrategy.AnalyzedSamplePoint(
                LocalDate.parse("2025-01-01"),
                "City Water",
                "Cooling Towers",
                "Iron",
                identities,
                "3+",
                "ppm",
                null,
                false,
                false,
                null,
                LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
            );

        LocationDashboardSample persisted = service.toPersistedSamples(location, List.of(sample)).getFirst();
        LocationDashboardImportStrategy.AnalyzedSamplePoint rehydrated = service.toAnalyzedSamplePoint(persisted);

        assertEquals(identities, rehydrated.identityValues());
    }

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
        assertTrue(persistedSamples.stream().allMatch(sample -> sample.getSampleIdentity().startsWith(
            LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX + "v2|"
        )));

        List<LocationDashboardImportStrategy.AnalyzedSamplePoint> rehydratedSamples = persistedSamples.stream()
            .map(service::toAnalyzedSamplePoint)
            .toList();

        assertEquals(3, rehydratedSamples.size());
        assertEquals(2, rehydratedSamples.stream().filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET).count());
        assertTrue(rehydratedSamples.stream()
            .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.WORKSHEET)
            .allMatch(sample -> sample.sampleIdentity() != null));
        LocationDashboardImportStrategy.AnalyzedSamplePoint rehydratedCommentSample = rehydratedSamples.stream()
            .filter(sample -> sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
            .findFirst()
            .orElseThrow();
        assertEquals("comment-sample-1", rehydratedCommentSample.sampleIdentity());
        assertEquals("POU 1", rehydratedCommentSample.identityValues().get("pointOfUse"));
    }

    @Test
    void rehydratesGeneratedIdentityFieldsFromSampleIdentity() {
        LocationDashboardSample sample = new LocationDashboardSample();
        sample.setObservedDate(LocalDate.parse("2025-01-01"));
        sample.setMeasurementName("HPC");
        sample.setRawValue("<1");
        sample.setSampleIdentity("__generated__|2025-01-01|Irvine||Critical SPD|Critical SPD|HPC|POU 1|Range|WORKSHEET|1");
        sample.setCompliant(false);
        sample.setOrigin(LocationDashboardImportStrategy.SampleOrigin.WORKSHEET.name());

        LocationDashboardImportStrategy.AnalyzedSamplePoint rehydratedSample = service.toAnalyzedSamplePoint(sample);

        assertEquals("__generated__|2025-01-01|Irvine||Critical SPD|Critical SPD|HPC|POU 1|Range|WORKSHEET|1", rehydratedSample.sampleIdentity());
        assertEquals("Irvine", rehydratedSample.facilityName());
        assertEquals("Critical SPD", rehydratedSample.identityValues().get("system"));
        assertEquals("POU 1", rehydratedSample.identityValues().get("pointOfUse"));
        assertEquals("Range", rehydratedSample.identityValues().get("basis"));
        assertEquals("<1", rehydratedSample.rawValue());
    }

    @Test
    void persistsAnalyzedSampleRawValues() {
        Location location = new Location();
        location.setId(10L);

        List<LocationDashboardSample> persistedSamples = service.toPersistedSamples(location, List.of(
            analyzedSample("HPC", "sample-1", "ND", true, LocationDashboardImportStrategy.SampleOrigin.WORKSHEET)
        ));

        assertEquals(1, persistedSamples.size());
        assertEquals("ND", persistedSamples.getFirst().getRawValue());
    }

    @Test
    void persistsAnalyzedSampleUnits() {
        Location location = new Location();
        location.setId(10L);

        List<LocationDashboardSample> persistedSamples = service.toPersistedSamples(location, List.of(
            analyzedSample("HPC", "sample-1", "12", "CFU.mL", false, LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY)
        ));

        assertEquals(1, persistedSamples.size());
        assertEquals("CFU.mL", persistedSamples.getFirst().getUnits());
        assertEquals("CFU.mL", service.toAnalyzedSamplePoint(persistedSamples.getFirst()).units());
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
        assertTrue(rehydratedSample.sampleIdentity()
            .startsWith(LocationDashboardSamplePersistenceService.GENERATED_SAMPLE_IDENTITY_PREFIX));
        assertEquals("HPC", rehydratedSample.measurementName());
        assertEquals(LocalDate.parse("2025-01-01"), rehydratedSample.observedDate());
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
            "Critical SPD",
            measurementName,
            identityValues("Irvine", "Irvine", "Critical SPD", "POU 1", "Range"),
            null,
            null,
            sampleIdentity,
            compliant,
            false,
            null,
            origin
        );
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample(
        String measurementName,
        String sampleIdentity,
        String rawValue,
        boolean compliant,
        LocationDashboardImportStrategy.SampleOrigin origin
    ) {
        return analyzedSample(measurementName, sampleIdentity, rawValue, null, compliant, origin);
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint analyzedSample(
        String measurementName,
        String sampleIdentity,
        String rawValue,
        String units,
        boolean compliant,
        LocationDashboardImportStrategy.SampleOrigin origin
    ) {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            LocalDate.parse("2025-01-01"),
            "Irvine",
            "Critical SPD",
            measurementName,
            identityValues("Irvine", "Irvine", "Critical SPD", "POU 1", "Range"),
            rawValue,
            units,
            sampleIdentity,
            compliant,
            false,
            null,
            origin
        );
    }
}
