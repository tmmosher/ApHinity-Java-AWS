package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.servicecalendar.LocationEventController;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.response.servicecalendar.ServiceEventResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.LocationEventService;
import com.aphinity.client_analytics_core.api.security.ClientRequestMetadataResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationEventControllerTest {
    @Mock
    private LocationEventService locationEventService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private ClientRequestMetadataResolver requestMetadataResolver;

    @InjectMocks
    private LocationEventController locationEventController;

    @Test
    void locationEventsDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();

        List<ServiceEventResponse> expected = List.of(response(31L, "Pump inspection"));
        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(locationEventService.getAccessibleLocationEvents(7L, 14L, YearMonth.of(2026, 4))).thenReturn(expected);

        List<ServiceEventResponse> actual = locationEventController.locationEvents(jwt, 14L, "2026-04");

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationEventService).getAccessibleLocationEvents(7L, 14L, YearMonth.of(2026, 4));
    }

    @Test
    void createLocationEventDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationEventRequest request = request("Service visit");
        ServiceEventResponse expected = response(19L, "Service visit");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationEventService.createLocationEvent(42L, 8L, request)).thenReturn(expected);

        ServiceEventResponse actual = locationEventController.createLocationEvent(jwt, 8L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationEventService).createLocationEvent(42L, 8L, request);
    }

    @Test
    void downloadLocationEventTemplateDelegatesToServiceAndReturnsAttachmentHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        Resource template = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "service_calendar_template.xlsx";
            }
        };

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationEventService.getServiceCalendarTemplate(42L, 8L)).thenReturn(template);

        ResponseEntity<Resource> actual = locationEventController.downloadLocationEventTemplate(jwt, 8L);

        assertSame(template, actual.getBody());
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            actual.getHeaders().getContentType().toString()
        );
        assertEquals(3L, actual.getHeaders().getContentLength());
        assertEquals(
            "attachment; filename=\"service_calendar_template.xlsx\"",
            actual.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
        );
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationEventService).getServiceCalendarTemplate(42L, 8L);
    }

    @Test
    void uploadLocationEventCalendarDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "service_calendar_upload.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{1, 2, 3}
        );

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationEventService.uploadServiceCalendar(42L, 8L, file)).thenReturn(3);

        var actual = locationEventController.uploadLocationEventCalendar(jwt, 8L, file);

        assertEquals(3, actual.importedCount());
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationEventService).uploadServiceCalendar(42L, 8L, file);
    }

    @Test
    void updateLocationEventDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationEventRequest request = request("Updated visit");
        ServiceEventResponse expected = response(19L, "Updated visit");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationEventService.updateLocationEvent(42L, 8L, 19L, request)).thenReturn(expected);

        ServiceEventResponse actual = locationEventController.updateLocationEvent(jwt, 8L, 19L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationEventService).updateLocationEvent(42L, 8L, 19L, request);
    }

    @Test
    void deleteLocationEventDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(requestMetadataResolver.resolveClientIp(request)).thenReturn("203.0.113.8");

        locationEventController.deleteLocationEvent(jwt, 8L, 19L, request);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(requestMetadataResolver).resolveClientIp(request);
        verify(locationEventService).deleteLocationEvent(42L, 8L, 19L, "203.0.113.8");
    }

    private LocationEventRequest request(String title) {
        return new LocationEventRequest(
            title,
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("11:00:00"),
            "Inspect service line",
            ServiceEventStatus.UPCOMING
        );
    }

    private ServiceEventResponse response(Long id, String title) {
        return new ServiceEventResponse(
            id,
            title,
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("09:30:00"),
            LocalDate.parse("2026-04-01"),
            LocalTime.parse("11:00:00"),
            "Inspect service line",
            ServiceEventStatus.UPCOMING,
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-03-02T00:00:00Z")
        );
    }
}
