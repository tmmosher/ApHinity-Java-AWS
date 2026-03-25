package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.repositories.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.requests.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.LocationEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationEventServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private ServiceEventRepository serviceEventRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @InjectMocks
    private LocationEventService locationEventService;

    @Test
    void getAccessibleLocationEventsReturnsOrderedResponsesForAuthorizedClient() {
        AppUser user = verifiedUser(5L);
        ServiceEvent first = serviceEvent(31L, "A visit");
        ServiceEvent second = serviceEvent(32L, "B visit");

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(99L, 5L)).thenReturn(true);
        when(serviceEventRepository.findByLocation_IdOrderByEventDateAscEventTimeAscIdAsc(99L))
            .thenReturn(List.of(first, second));

        List<ServiceEventResponse> response = locationEventService.getAccessibleLocationEvents(5L, 99L);

        assertEquals(List.of("A visit", "B visit"), response.stream().map(ServiceEventResponse::title).toList());
        verify(serviceEventRepository).findByLocation_IdOrderByEventDateAscEventTimeAscIdAsc(99L);
    }

    @Test
    void getAccessibleLocationEventsRejectsUnauthorizedClient() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(99L, 5L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.getAccessibleLocationEvents(5L, 99L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(serviceEventRepository);
    }

    @Test
    void createLocationEventAllowsClientWhenResponsibilityIsClientAndLocationIsAccessible() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(99L, 5L)).thenReturn(true);
        when(serviceEventRepository.saveAndFlush(any(ServiceEvent.class))).thenAnswer(invocation -> {
            ServiceEvent event = invocation.getArgument(0);
            event.setId(55L);
            event.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            event.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
            return event;
        });

        ServiceEventResponse response = locationEventService.createLocationEvent(
            5L,
            99L,
            request("Client visit", "Client-owned task", ServiceEventResponsibility.CLIENT)
        );

        assertEquals(55L, response.id());
        assertEquals("Client visit", response.title());
        assertEquals(ServiceEventResponsibility.CLIENT, response.responsibility());
        verify(serviceEventRepository).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void createLocationEventRejectsClientForPartnerResponsibility() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.createLocationEvent(
                5L,
                99L,
                request("Partner visit", "Partner-owned task", ServiceEventResponsibility.PARTNER)
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void createLocationEventPersistsNormalizedFieldsAndTouchesLocation() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));
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
        verify(serviceEventRepository).saveAndFlush(any(ServiceEvent.class));
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
    }

    @Test
    void createLocationEventRejectsBlankTitle() {
        AppUser user = verifiedUser(5L);
        Location location = new Location();
        location.setId(99L);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(locationRepository.findById(99L)).thenReturn(Optional.of(location));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.createLocationEvent(5L, 99L, request("   ", null, ServiceEventResponsibility.PARTNER))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Event title is required", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void updateLocationEventRejectsMissingEvent() {
        AppUser user = verifiedUser(5L);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.updateLocationEvent(5L, 99L, 44L, request("Updated visit"))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Location event not found", ex.getReason());
    }

    @Test
    void updateLocationEventAllowsClientForClientResponsibility() {
        AppUser user = verifiedUser(5L);
        ServiceEvent serviceEvent = serviceEvent(44L, "Client event");
        serviceEvent.setResponsibility(ServiceEventResponsibility.CLIENT);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(99L, 5L)).thenReturn(true);
        when(serviceEventRepository.saveAndFlush(serviceEvent)).thenReturn(serviceEvent);

        ServiceEventResponse response = locationEventService.updateLocationEvent(
            5L,
            99L,
            44L,
            request("Updated client event", "Updated description", ServiceEventResponsibility.CLIENT)
        );

        assertEquals("Updated client event", response.title());
        assertEquals(ServiceEventResponsibility.CLIENT, response.responsibility());
        verify(serviceEventRepository).saveAndFlush(serviceEvent);
    }

    @Test
    void updateLocationEventRejectsClientWhenChangingResponsibilityToPartner() {
        AppUser user = verifiedUser(5L);
        ServiceEvent serviceEvent = serviceEvent(44L, "Client event");
        serviceEvent.setResponsibility(ServiceEventResponsibility.CLIENT);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.updateLocationEvent(
                5L,
                99L,
                44L,
                request("Partner takeover", "Updated description", ServiceEventResponsibility.PARTNER)
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void updateLocationEventRejectsClientForPartnerOwnedEvent() {
        AppUser user = verifiedUser(5L);
        ServiceEvent serviceEvent = serviceEvent(44L, "Partner event");
        serviceEvent.setResponsibility(ServiceEventResponsibility.PARTNER);

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(false);
        when(locationRepository.existsById(99L)).thenReturn(true);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationEventService.updateLocationEvent(
                5L,
                99L,
                44L,
                request("Client attempt", "Updated description", ServiceEventResponsibility.CLIENT)
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verify(serviceEventRepository, never()).saveAndFlush(any(ServiceEvent.class));
    }

    @Test
    void deleteLocationEventDeletesExistingEvent() {
        AppUser user = verifiedUser(5L);
        ServiceEvent serviceEvent = serviceEvent(44L, "Delete me");

        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(accountRoleService.isPartnerOrAdmin(user)).thenReturn(true);
        when(serviceEventRepository.findByIdAndLocation_Id(44L, 99L)).thenReturn(Optional.of(serviceEvent));

        locationEventService.deleteLocationEvent(5L, 99L, 44L);

        verify(serviceEventRepository).delete(serviceEvent);
        verify(locationRepository).touchUpdatedAt(eq(99L), any(Instant.class));
        verify(locationRepository, never()).existsById(99L);
    }

    private LocationEventRequest request(String title) {
        return request(title, "Inspect line", ServiceEventResponsibility.PARTNER);
    }

    private LocationEventRequest request(
        String title,
        String description,
        ServiceEventResponsibility responsibility
    ) {
        return new LocationEventRequest(
            title,
            responsibility,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
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
        serviceEvent.setDescription("Inspect line");
        serviceEvent.setStatus(ServiceEventStatus.UPCOMING);
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
}
