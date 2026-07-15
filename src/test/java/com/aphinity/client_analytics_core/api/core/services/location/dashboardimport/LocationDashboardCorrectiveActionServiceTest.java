package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationDashboardCorrectiveActionServiceTest {

    @Test
    void dynamicIdentityComparisonDoesNotDependOnMapInsertionOrder() {
        Map<String, String> firstValues = new LinkedHashMap<>();
        firstValues.put("sampling station", "Recirc Line");
        firstValues.put("water train", "Cooling Towers");
        String first = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
            "HPC",
            LocalDate.parse("2025-08-01"),
            firstValues,
            null
        );
        Map<String, String> reversedValues = new LinkedHashMap<>();
        reversedValues.put("water train", "Cooling Towers");
        reversedValues.put("sampling station", "Recirc Line");
        String second = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
            "HPC",
            LocalDate.parse("2025-08-01"),
            reversedValues,
            null
        );

        assertEquals(first, second);
    }
    @Mock
    private ServiceEventRepository serviceEventRepository;

    @Test
    void buildPreviewCorrectiveActionsMarksResolvedDraftsCompleted() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of());

        List<ServiceEvent> previewEvents = service.buildPreviewCorrectiveActions(
            9L,
            List.of(draft(true))
        );

        assertEquals(ServiceEventStatus.COMPLETED, previewEvents.getFirst().getStatus());
        assertEquals(LocalDate.parse("2025-08-09"), previewEvents.getFirst().getEndEventDate());
    }

    @Test
    void buildPreviewCorrectiveActionsMarksMatchedPersistedEventsCompletedWhenDraftResolved() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        LocationDashboardImportStrategy.CorrectiveActionDraft draft = draft(true);
        ServiceEvent persisted = serviceEvent(draft);
        persisted.setStatus(ServiceEventStatus.OVERDUE);
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(persisted));

        List<ServiceEvent> previewEvents = service.buildPreviewCorrectiveActions(
            9L,
            List.of(draft)
        );

        assertEquals(ServiceEventStatus.COMPLETED, previewEvents.getFirst().getStatus());
        assertEquals(LocalDate.parse("2025-08-09"), previewEvents.getFirst().getEndEventDate());
    }

    @Test
    void persistCorrectiveActionsMarksMatchedPersistedEventsCompletedWhenDraftResolved() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        Location location = new Location();
        location.setId(9L);
        LocationDashboardImportStrategy.CorrectiveActionDraft draft = draft(true);
        ServiceEvent persisted = serviceEvent(draft);
        persisted.setStatus(ServiceEventStatus.OVERDUE);
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(persisted));

        List<ServiceEvent> events = service.persistCorrectiveActions(location, List.of(draft));

        assertEquals(ServiceEventStatus.COMPLETED, events.getFirst().getStatus());
        assertEquals(LocalDate.parse("2025-08-09"), events.getFirst().getEndEventDate());
        verify(serviceEventRepository).saveAllAndFlush(List.of(persisted));
    }

    @Test
    void completeResolvedPersistedCorrectiveActionsCompletesOnlyMatchingResolvedDrafts() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        Location location = new Location();
        location.setId(9L);
        LocationDashboardImportStrategy.CorrectiveActionDraft resolvedDraft = draft(true);
        LocationDashboardImportStrategy.CorrectiveActionDraft unresolvedDraft = draft(false);
        ServiceEvent matchingPersisted = serviceEvent(resolvedDraft);
        matchingPersisted.setStatus(ServiceEventStatus.OVERDUE);
        ServiceEvent unmatchedPersisted = serviceEvent(unresolvedDraft);
        unmatchedPersisted.setId(2L);
        unmatchedPersisted.setTitle("Different CA");
        unmatchedPersisted.setDescription(String.join("\n", List.of(
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine("Legionella"),
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-08-01")),
            LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Newport Beach"),
            LocationDashboardCorrectiveActionMetadataSupport.systemLine("Cooling Towers")
        )));
        unmatchedPersisted.setStatus(ServiceEventStatus.OVERDUE);
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(matchingPersisted, unmatchedPersisted));

        service.completeResolvedPersistedCorrectiveActions(location, List.of(resolvedDraft));

        assertEquals(ServiceEventStatus.COMPLETED, matchingPersisted.getStatus());
        assertEquals(LocalDate.parse("2025-08-09"), matchingPersisted.getEndEventDate());
        assertEquals(ServiceEventStatus.OVERDUE, unmatchedPersisted.getStatus());
        verify(serviceEventRepository).saveAllAndFlush(List.of(matchingPersisted));
    }

    @Test
    void completeResolvedPersistedCorrectiveActionsRepairsCompletedEventResolutionDate() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        Location location = new Location();
        location.setId(9L);
        LocationDashboardImportStrategy.CorrectiveActionDraft resolvedDraft = draft(true);
        ServiceEvent matchingPersisted = serviceEvent(resolvedDraft);
        matchingPersisted.setStatus(ServiceEventStatus.COMPLETED);
        matchingPersisted.setEndEventDate(LocalDate.parse("2025-08-01"));
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(matchingPersisted));

        service.completeResolvedPersistedCorrectiveActions(location, List.of(resolvedDraft));

        assertEquals(ServiceEventStatus.COMPLETED, matchingPersisted.getStatus());
        assertEquals(LocalDate.parse("2025-08-09"), matchingPersisted.getEndEventDate());
        verify(serviceEventRepository).saveAllAndFlush(List.of(matchingPersisted));
    }

    @Test
    void buildPreviewCorrectiveActionsMatchesLegacyPersistedEventWithoutSampleIdentity() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        LocationDashboardImportStrategy.CorrectiveActionDraft draft = draftWithSampleIdentity(true);
        ServiceEvent persisted = serviceEvent(draft(false));
        persisted.setStatus(ServiceEventStatus.OVERDUE);
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(persisted));

        List<ServiceEvent> previewEvents = service.buildPreviewCorrectiveActions(
            9L,
            List.of(draft)
        );

        assertEquals(1, previewEvents.size());
        assertEquals(1L, previewEvents.getFirst().getId());
        assertEquals(ServiceEventStatus.COMPLETED, previewEvents.getFirst().getStatus());
    }

    @Test
    void toHistoricalCorrectiveActionPrefersReservedStructuredMetadataOverCommentText() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );

        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(1L);
        serviceEvent.setLocation(new Location());
        serviceEvent.setCorrectiveAction(true);
        serviceEvent.setEventDate(LocalDate.parse("2025-08-01"));
        serviceEvent.setEventTime(LocalTime.MIDNIGHT);
        serviceEvent.setDescription(String.join("\n", java.util.List.of(
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine("HPC"),
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-08-01")),
            LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Newport Beach"),
            LocationDashboardCorrectiveActionMetadataSupport.systemLine("Cooling Towers"),
            "",
            "System: User Override",
            "Measurement: User Override"
        )));

        LocationDashboardDerivedGraphSupport.HistoricalCorrectiveAction historical = service.toHistoricalCorrectiveAction(serviceEvent);

        assertEquals("Cooling Towers", historical.identityValues().get("system"));
        assertEquals("HPC", historical.measurementName());
        assertEquals("Newport Beach", historical.facilityName());
    }

    private LocationDashboardImportStrategy.CorrectiveActionDraft draft(boolean resolved) {
        return new LocationDashboardImportStrategy.CorrectiveActionDraft(
            LocalDate.parse("2025-08-01"),
            "CA: HPC 2025-08-01",
            String.join("\n", List.of(
                LocationDashboardCorrectiveActionMetadataSupport.measurementLine("HPC"),
                LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-08-01")),
                LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Newport Beach"),
                LocationDashboardCorrectiveActionMetadataSupport.systemLine("Cooling Towers")
            )),
            "Newport Beach",
            "Cooling Towers",
            "HPC",
            resolved,
            resolved ? LocalDate.parse("2025-08-09") : null
        );
    }

    private LocationDashboardImportStrategy.CorrectiveActionDraft draftWithSampleIdentity(boolean resolved) {
        return new LocationDashboardImportStrategy.CorrectiveActionDraft(
            LocalDate.parse("2025-08-01"),
            "CA: HPC 2025-08-01",
            String.join("\n", List.of(
                LocationDashboardCorrectiveActionMetadataSupport.measurementLine("HPC"),
                LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-08-01")),
                LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Newport Beach"),
                LocationDashboardCorrectiveActionMetadataSupport.systemLine("Cooling Towers"),
                LocationDashboardCorrectiveActionMetadataSupport.sampleIdentityLine("primary-sample|2025-08-01|2025-08-09|140")
            )),
            "Newport Beach",
            "Cooling Towers",
            "HPC",
            resolved,
            resolved ? LocalDate.parse("2025-08-09") : null
        );
    }

    private ServiceEvent serviceEvent(LocationDashboardImportStrategy.CorrectiveActionDraft draft) {
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(1L);
        serviceEvent.setCorrectiveAction(true);
        serviceEvent.setTitle(draft.title());
        serviceEvent.setDescription(draft.description());
        serviceEvent.setEventDate(draft.observedDate());
        serviceEvent.setEventTime(LocalTime.MIDNIGHT);
        serviceEvent.setEndEventDate(draft.observedDate());
        serviceEvent.setEndEventTime(LocalTime.of(23, 59, 59));
        return serviceEvent;
    }

    @Test
    void toHistoricalCorrectiveActionDropsEventsWithoutRequiredStructuredMetadata() {
        LocationDashboardCorrectiveActionService service = new LocationDashboardCorrectiveActionService(
            serviceEventRepository,
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );

        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(2L);
        serviceEvent.setLocation(new Location());
        serviceEvent.setCorrectiveAction(true);
        serviceEvent.setEventDate(LocalDate.parse("2025-08-01"));
        serviceEvent.setEventTime(LocalTime.MIDNIGHT);
        serviceEvent.setDescription("Legacy corrective action without import metadata");

        assertNull(service.toHistoricalCorrectiveAction(serviceEvent));
    }
}
