package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class LocationDashboardCorrectiveActionServiceTest {
    @Mock
    private ServiceEventRepository serviceEventRepository;

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

        assertEquals("Cooling Towers", historical.systemTypeName());
        assertEquals("HPC", historical.measurementName());
        assertEquals("Newport Beach", historical.facilityName());
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
