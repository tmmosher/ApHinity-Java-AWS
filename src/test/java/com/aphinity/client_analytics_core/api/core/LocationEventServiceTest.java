package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.LocationEventService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceCalendarAuthorizationService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceCalendarImportService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceCalendarTemplateService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceEventAuditService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceEventRequestMapper;
import com.aphinity.client_analytics_core.api.notifications.MailOutboxCommandService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationEventServiceTest {
    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ServiceEventRepository serviceEventRepository;

    @Mock
    private ServiceCalendarAuthorizationService authorizationService;

    @Spy
    private ServiceEventRequestMapper requestMapper = new ServiceEventRequestMapper();

    @Spy
    private ServiceCalendarTemplateService templateService = new ServiceCalendarTemplateService();

    @Mock
    private ServiceCalendarImportService importService;

    @Mock
    private ServiceEventAuditService auditService;

    @Mock
    private MailOutboxCommandService mailOutboxService;

    @InjectMocks
    private LocationEventService locationEventService;

    @Test
    void getAccessibleLocationEventsReturnsOrderedResponsesForAuthorizedClient() {
        AppUser user = verifiedUser(5L);
        ServiceEvent first = serviceEvent(31L, "A visit");
        ServiceEvent second = serviceEvent(32L, "B visit");

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);
        when(serviceEventRepository.findVisibleByLocationIdAndDateWindow(
            99L,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-05-31")
        )).thenReturn(List.of(first, second));

        List<ServiceEventResponse> response = locationEventService.getAccessibleLocationEvents(
            5L,
            99L,
            YearMonth.of(2026, 4)
        );

        assertEquals(List.of("A visit", "B visit"), response.stream().map(ServiceEventResponse::title).toList());
        verify(authorizationService).requireUser(5L);
        verify(authorizationService).requireReadableLocationAccess(user, 99L);
        verify(serviceEventRepository).findVisibleByLocationIdAndDateWindow(
            99L,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-05-31")
        );
    }

    @Test
    void getAccessibleLocationEventsReturnsCorrectiveActionWithoutDeletedSourceEvent() {
        AppUser user = verifiedUser(5L);
        GuardedServiceEvent sourceEvent = new GuardedServiceEvent();
        sourceEvent.setId(3L);
        sourceEvent.setTitle("Source Event");
        sourceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
        sourceEvent.setEventDate(LocalDate.parse("2026-04-01"));
        sourceEvent.setEventTime(LocalTime.parse("08:00:00"));
        sourceEvent.setEndEventDate(LocalDate.parse("2026-04-01"));
        sourceEvent.setEndEventTime(LocalTime.parse("09:00:00"));
        sourceEvent.setDescription("Original issue");
        sourceEvent.setStatus(ServiceEventStatus.UPCOMING);
        sourceEvent.setCorrectiveAction(false);
        sourceEvent.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        sourceEvent.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        sourceEvent.markDetached();

        ServiceEvent correctiveAction = serviceEvent(44L, "Corrective Action");
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setCorrectiveActionSourceEvent(sourceEvent);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);
        when(serviceEventRepository.findVisibleByLocationIdAndDateWindow(
            99L,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-05-31")
        )).thenReturn(List.of(correctiveAction));

        List<ServiceEventResponse> response = locationEventService.getAccessibleLocationEvents(
            5L,
            99L,
            YearMonth.of(2026, 4)
        );

        assertEquals(1, response.size());
        assertTrue(response.getFirst().correctiveAction());
        assertEquals(3L, response.getFirst().correctiveActionSourceEventId());
        assertNull(response.getFirst().correctiveActionSourceEventTitle());
    }

    @Test
    void getAccessibleLocationEventsPropagatesAuthorizationFailure() {
        AppUser user = verifiedUser(5L);
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doThrow(forbidden).when(authorizationService).requireReadableLocationAccess(user, 99L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.getAccessibleLocationEvents(5L, 99L, YearMonth.of(2026, 4))
        );

        assertSame(forbidden, ex);
        verifyNoInteractions(serviceEventRepository);
    }

    @Test
    void getServiceCalendarTemplateReturnsClassPathResourceForAuthorizedClient() throws Exception {
        AppUser user = verifiedUser(5L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireReadableLocationAccess(user, 99L);

        Resource resource = locationEventService.getServiceCalendarTemplate(5L, 99L);

        assertEquals("service_calendar_template.xlsx", resource.getFilename());
        assertTrue(resource.exists());
        assertTrue(resource.contentLength() > 0);
    }

    @Test
    void uploadServiceCalendarDelegatesToImportService() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "service_calendar_upload.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{1, 2, 3}
        );

        when(importService.uploadServiceCalendar(5L, 99L, file)).thenReturn(3);

        int importedCount = locationEventService.uploadServiceCalendar(5L, 99L, file);

        assertEquals(3, importedCount);
        verify(importService).uploadServiceCalendar(5L, 99L, file);
        verifyNoInteractions(authorizationService, locationRepository, serviceEventRepository, auditService);
    }

    @Test
    void createLocationEventPersistsNormalizedFieldsAndTouchesLocation() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        when(authorizationService.requireLocation(99L)).thenReturn(location);
        doNothing().when(authorizationService).requireCreatePermission(user, 99L, ServiceEventResponsibility.PARTNER);
        when(serviceEventRepository.saveAndFlush(any(ServiceEvent.class))).thenAnswer(invocation -> {
            ServiceEvent event = invocation.getArgument(0);
            event.setId(44L);
            event.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            event.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return event;
        });

        ServiceEventResponse response = locationEventService.createLocationEvent(
            5L,
            99L,
            request("  Service visit  ", "  Inspect line pressure  ", ServiceEventResponsibility.PARTNER)
        );

        assertEquals(44L, response.id());
        assertEquals("Service visit", response.title());
        assertEquals("Inspect line pressure", response.description());
        assertEquals(LocalDate.parse("2026-04-01"), response.endDate());
        assertEquals(LocalTime.parse("11:00:00"), response.endTime());
        verify(serviceEventRepository).saveAndFlush(any(ServiceEvent.class));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
        verify(auditService).recordCreated(eq(5L), any(ServiceEvent.class));
    }

    @Test
    void createLocationEventRejectsMissingResponsibilityBeforePersistence() {
        AppUser user = verifiedUser(5L);
        LocationEventRequest request = new LocationEventRequest(
            "Service visit",
            null,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
            null,
            null,
            "Inspect line pressure",
            ServiceEventStatus.UPCOMING
        );

        when(authorizationService.requireUser(5L)).thenReturn(user);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.createLocationEvent(5L, 99L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Event responsibility is required", ex.getReason());
        verifyNoInteractions(serviceEventRepository, locationRepository, auditService);
    }

    @Test
    void createCorrectiveActionPersistsWithBacklinkAndQueuesWorkOrderEmailForPartnerAdmin() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);
        location.setName("Austin");
        location.setWorkOrderEmail("work-orders@example.com");
        ServiceEvent sourceEvent = serviceEvent(44L, "Source Event");
        sourceEvent.setLocation(location);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(sourceEvent));
        doNothing().when(authorizationService).requireCreateCorrectiveActionPermission(user, 99L, sourceEvent);
        doNothing().when(authorizationService).requireCreatePermission(user, 99L, ServiceEventResponsibility.PARTNER);
        when(serviceEventRepository.saveAndFlush(any(ServiceEvent.class))).thenAnswer(invocation -> {
            ServiceEvent event = invocation.getArgument(0);
            event.setId(45L);
            event.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            event.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return event;
        });

        ServiceEventResponse response = locationEventService.createCorrectiveActionForLocationEvent(
            5L,
            99L,
            44L,
            request("Corrective Action", "Fix it", ServiceEventResponsibility.PARTNER)
        );

        assertEquals(45L, response.id());
        assertEquals("Corrective Action", response.title());
        assertTrue(response.correctiveAction());
        assertEquals(44L, response.correctiveActionSourceEventId());
        assertEquals("Source Event", response.correctiveActionSourceEventTitle());
        verify(serviceEventRepository).saveAndFlush(any(ServiceEvent.class));
        verify(auditService).recordCreated(eq(5L), any(ServiceEvent.class));
        verify(authorizationService).requireCreateCorrectiveActionPermission(user, 99L, sourceEvent);
        verify(mailOutboxService).queueWorkOrderEmail(
            eq(5L),
            eq("Austin"),
            eq("work-orders@example.com"),
            eq("Corrective Action"),
            eq("Fix it")
        );
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void createCorrectiveActionQueuesWorkOrderEmailForClients() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);
        location.setName("Austin");
        location.setWorkOrderEmail("work-orders@example.com");
        ServiceEvent sourceEvent = serviceEvent(44L, "Source Event");
        sourceEvent.setLocation(location);
        sourceEvent.setResponsibility(ServiceEventResponsibility.CLIENT);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(sourceEvent));
        doNothing().when(authorizationService).requireCreateCorrectiveActionPermission(user, 99L, sourceEvent);
        doNothing().when(authorizationService).requireCreatePermission(user, 99L, ServiceEventResponsibility.CLIENT);
        when(serviceEventRepository.saveAndFlush(any(ServiceEvent.class))).thenAnswer(invocation -> {
            ServiceEvent event = invocation.getArgument(0);
            event.setId(45L);
            event.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            event.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return event;
        });

        ServiceEventResponse response = locationEventService.createCorrectiveActionForLocationEvent(
            5L,
            99L,
            44L,
            request("Corrective Action", "Fix it", ServiceEventResponsibility.CLIENT)
        );

        assertTrue(response.correctiveAction());
        assertEquals(44L, response.correctiveActionSourceEventId());
        verify(authorizationService).requireCreateCorrectiveActionPermission(user, 99L, sourceEvent);
        verify(mailOutboxService).queueWorkOrderEmail(
            eq(5L),
            eq("Austin"),
            eq("work-orders@example.com"),
            eq("Corrective Action"),
            eq("Fix it")
        );
    }

    @Test
    void createCorrectiveActionRejectsMissingWorkOrderEmailBeforePersistence() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);
        location.setName("Austin");
        ServiceEvent sourceEvent = serviceEvent(44L, "Source Event");
        sourceEvent.setLocation(location);
        sourceEvent.setResponsibility(ServiceEventResponsibility.CLIENT);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(sourceEvent));
        doNothing().when(authorizationService).requireCreateCorrectiveActionPermission(user, 99L, sourceEvent);
        doNothing().when(authorizationService).requireCreatePermission(user, 99L, ServiceEventResponsibility.CLIENT);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.createCorrectiveActionForLocationEvent(
                5L,
                99L,
                44L,
                request("Corrective Action", "Fix it", ServiceEventResponsibility.CLIENT)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Location work-order email is required", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
        verify(mailOutboxService, never()).queueWorkOrderEmail(any(), any(), any(), any(), any());
    }

    @Test
    void updateLocationEventPersistsUpdatedFieldsAndTouchesLocation() {
        AppUser user = verifiedUser(5L);
        LocationEventRequest request = request(
            "  Updated visit  ",
            "  Updated description  ",
            ServiceEventResponsibility.CLIENT,
            LocalDate.parse("2026-04-02"),
            LocalTime.parse("12:00:00")
        );
        ServiceEvent serviceEvent = serviceEvent(44L, "Client event");
        serviceEvent.setResponsibility(ServiceEventResponsibility.CLIENT);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));
        doNothing().when(authorizationService).requireUpdatePermission(user, 99L, serviceEvent, ServiceEventResponsibility.CLIENT);
        when(serviceEventRepository.saveAndFlush(serviceEvent)).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceEventResponse response = locationEventService.updateLocationEvent(5L, 99L, 44L, request);

        assertEquals("Updated visit", response.title());
        assertEquals(ServiceEventResponsibility.CLIENT, response.responsibility());
        assertEquals(LocalDate.parse("2026-04-02"), response.endDate());
        assertEquals(LocalTime.parse("12:00:00"), response.endTime());
        verify(serviceEventRepository).saveAndFlush(serviceEvent);
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
        verify(auditService).recordUpdated(eq(5L), any(ServiceEvent.class));
    }

    @Test
    void updateCorrectiveActionMapsResponseBeforeTouchingLocation() {
        AppUser user = verifiedUser(5L);
        LocationEventRequest request = new LocationEventRequest(
            "Completed corrective action",
            ServiceEventResponsibility.CLIENT,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("11:00:00"),
            "Fix the issue",
            ServiceEventStatus.COMPLETED
        );

        GuardedServiceEvent sourceEvent = new GuardedServiceEvent();
        sourceEvent.setId(3L);
        sourceEvent.setTitle("Source Event");
        sourceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
        sourceEvent.setEventDate(LocalDate.parse("2026-04-01"));
        sourceEvent.setEventTime(LocalTime.parse("08:00:00"));
        sourceEvent.setEndEventDate(LocalDate.parse("2026-04-01"));
        sourceEvent.setEndEventTime(LocalTime.parse("09:00:00"));
        sourceEvent.setDescription("Original issue");
        sourceEvent.setStatus(ServiceEventStatus.UPCOMING);
        sourceEvent.setCorrectiveAction(false);
        sourceEvent.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        sourceEvent.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));

        ServiceEvent correctiveAction = serviceEvent(44L, "Corrective Action");
        correctiveAction.setResponsibility(ServiceEventResponsibility.CLIENT);
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setCorrectiveActionSourceEvent(sourceEvent);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(correctiveAction));
        doNothing().when(authorizationService).requireUpdatePermission(
            user,
            99L,
            correctiveAction,
            ServiceEventResponsibility.CLIENT
        );
        when(serviceEventRepository.saveAndFlush(correctiveAction)).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            sourceEvent.markDetached();
            return 1;
        }).when(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));

        InOrder inOrder = org.mockito.Mockito.inOrder(serviceEventRepository, requestMapper, locationRepository);

        ServiceEventResponse response = locationEventService.updateLocationEvent(5L, 99L, 44L, request);

        assertTrue(response.correctiveAction());
        assertEquals(ServiceEventStatus.COMPLETED, response.status());
        assertEquals(3L, response.correctiveActionSourceEventId());
        assertEquals("Source Event", response.correctiveActionSourceEventTitle());
        inOrder.verify(serviceEventRepository).saveAndFlush(correctiveAction);
        inOrder.verify(requestMapper).toResponse(correctiveAction);
        inOrder.verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void updateLocationEventRejectsMissingEvent() {
        AppUser user = verifiedUser(5L);
        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireLocationExists(99L);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.updateLocationEvent(5L, 99L, 44L, request("Updated visit"))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location event not found", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void deleteLocationEventDeletesExistingEvent() {
        AppUser user = verifiedUser(5L);
        ServiceEvent serviceEvent = serviceEvent(44L, "Delete me");
        String clientIpAddress = "203.0.113.8";

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireDeletePermission(user);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));

        locationEventService.deleteLocationEvent(5L, 99L, 44L, clientIpAddress);

        verify(auditService).recordDeleted(5L, clientIpAddress, serviceEvent);
        verify(serviceEventRepository).clearCorrectiveActionSourceEvent(99L, 44L);
        verify(serviceEventRepository).delete(serviceEvent);
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void deleteLocationEventRejectsMissingEvent() {
        AppUser user = verifiedUser(5L);

        when(authorizationService.requireUser(5L)).thenReturn(user);
        doNothing().when(authorizationService).requireDeletePermission(user);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.deleteLocationEvent(5L, 99L, 44L, "203.0.113.8")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location event not found", ex.getReason());
        verify(authorizationService).requireLocationExists(99L);
        verify(serviceEventRepository, never()).delete(any(ServiceEvent.class));
    }

    private LocationEventRequest request(String title) {
        return request(title, "Inspect line", ServiceEventResponsibility.PARTNER);
    }

    private LocationEventRequest request(
        String title,
        String description,
        ServiceEventResponsibility responsibility
    ) {
        return request(
            title,
            description,
            responsibility,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("11:00:00")
        );
    }

    private LocationEventRequest request(
        String title,
        String description,
        ServiceEventResponsibility responsibility,
        LocalDate endDate,
        LocalTime endTime
    ) {
        return new LocationEventRequest(
            title,
            responsibility,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
            endDate,
            endTime,
            description,
            ServiceEventStatus.UPCOMING
        );
    }

    private ServiceEvent serviceEvent(Long id, String title) {
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(id);
        serviceEvent.setTitle(title);
        serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);
        serviceEvent.setEventDate(LocalDate.parse("2026-04-01"));
        serviceEvent.setEventTime(LocalTime.parse("09:30:00"));
        serviceEvent.setEndEventDate(LocalDate.parse("2026-04-01"));
        serviceEvent.setEndEventTime(LocalTime.parse("11:00:00"));
        serviceEvent.setDescription("Inspect line");
        serviceEvent.setStatus(ServiceEventStatus.UPCOMING);
        serviceEvent.setCorrectiveAction(false);
        serviceEvent.setCorrectiveActionSourceEvent(null);
        serviceEvent.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        serviceEvent.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));
        return serviceEvent;
    }

    private AppUser verifiedUser(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("verified@example.com");
        user.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private static final class GuardedServiceEvent extends ServiceEvent {
        private boolean detached;

        void markDetached() {
            detached = true;
        }

        @Override
        public String getTitle() {
            if (detached) {
                throw new EntityNotFoundException("Could not initialize proxy [ServiceEvent#3] - no session");
            }
            return super.getTitle();
        }
    }
}
