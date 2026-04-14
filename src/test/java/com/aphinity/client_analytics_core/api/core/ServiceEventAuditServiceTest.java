package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceEventAuditService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceEventAuditServiceTest {
    @Mock
    private AsyncLogService asyncLogService;

    @Test
    void recordDeletedWritesServiceEventAuditLineToAsyncLogger() {
        ServiceEventAuditService auditService = new ServiceEventAuditService(asyncLogService);
        ServiceEvent serviceEvent = serviceEvent();

        auditService.recordDeleted(5L, "203.0.113.8", serviceEvent);

        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncLogService).log(logMessageCaptor.capture());
        String logMessage = logMessageCaptor.getValue();
        assertTrue(logMessage.contains("service-event-audit"));
        assertTrue(logMessage.contains("action=DELETED"));
        assertTrue(logMessage.contains("actorUserId=5"));
        assertTrue(logMessage.contains("actorIpAddress=\"203.0.113.8\""));
        assertTrue(logMessage.contains("title=\"Delete me\""));
        assertTrue(logMessage.contains("eventDate=2026-04-01"));
        assertTrue(logMessage.contains("endEventDate=2026-04-01"));
        assertTrue(logMessage.contains("responsibility=PARTNER"));
    }

    private ServiceEvent serviceEvent() {
        Location location = new Location();
        location.setId(99L);

        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(44L);
        serviceEvent.setLocation(location);
        serviceEvent.setTitle("Delete me");
        serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
        serviceEvent.setEventDate(LocalDate.parse("2026-04-01"));
        serviceEvent.setEventTime(LocalTime.parse("09:30:00"));
        serviceEvent.setEndEventDate(LocalDate.parse("2026-04-01"));
        serviceEvent.setEndEventTime(LocalTime.parse("11:00:00"));
        serviceEvent.setDescription("Inspect line");
        serviceEvent.setStatus(ServiceEventStatus.UPCOMING);
        serviceEvent.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        serviceEvent.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        return serviceEvent;
    }
}
