package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.gantt.LocationGanttTaskController;
import com.aphinity.client_analytics_core.api.core.requests.gantt.LocationGanttTaskRequest;
import com.aphinity.client_analytics_core.api.core.response.gantt.GanttTaskResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.gantt.LocationGanttTaskService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationGanttTaskControllerTest {
    @Mock
    private LocationGanttTaskService locationGanttTaskService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private ClientRequestMetadataResolver requestMetadataResolver;

    @InjectMocks
    private LocationGanttTaskController locationGanttTaskController;

    @Test
    void ganttTasksDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("7")
            .build();
        List<GanttTaskResponse> expected = List.of(response(31L, "OPS"));

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(7L);
        when(locationGanttTaskService.getAccessibleLocationTasks(7L, 14L, "ops")).thenReturn(expected);

        List<GanttTaskResponse> actual = locationGanttTaskController.ganttTasks(jwt, 14L, "ops");

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationGanttTaskService).getAccessibleLocationTasks(7L, 14L, "ops");
    }

    @Test
    void downloadLocationGanttTaskTemplateDelegatesToServiceAndReturnsAttachmentHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        Resource template = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "gantt_chart_template.xlsx";
            }
        };

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationGanttTaskService.getGanttChartTemplate(42L, 8L)).thenReturn(template);

        ResponseEntity<Resource> actual = locationGanttTaskController.downloadLocationGanttTaskTemplate(jwt, 8L);

        assertSame(template, actual.getBody());
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            Objects.requireNonNull(actual.getHeaders().getContentType()).toString()
        );
        assertEquals(3L, actual.getHeaders().getContentLength());
        assertEquals(
            "attachment; filename=\"gantt_chart_template.xlsx\"",
            actual.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
        );
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationGanttTaskService).getGanttChartTemplate(42L, 8L);
    }

    @Test
    void createGanttTaskDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGanttTaskRequest request = request("OPS");
        GanttTaskResponse expected = response(19L, "OPS");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationGanttTaskService.createLocationTask(42L, 8L, request)).thenReturn(expected);

        GanttTaskResponse actual = locationGanttTaskController.createGanttTask(jwt, 8L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationGanttTaskService).createLocationTask(42L, 8L, request);
    }

    @Test
    void createGanttTasksBulkDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        List<LocationGanttTaskRequest> requests = List.of(
            request("OPS"),
            request("QMS")
        );
        List<GanttTaskResponse> expected = List.of(response(19L, "OPS"), response(20L, "QMS"));

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationGanttTaskService.createLocationTasksBulk(42L, 8L, requests)).thenReturn(expected);

        List<GanttTaskResponse> actual = locationGanttTaskController.createGanttTasksBulk(jwt, 8L, requests);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationGanttTaskService).createLocationTasksBulk(42L, 8L, requests);
    }

    @Test
    void updateGanttTaskDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        LocationGanttTaskRequest request = request("OPS");
        GanttTaskResponse expected = response(19L, "OPS");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(locationGanttTaskService.updateLocationTask(42L, 8L, 19L, request)).thenReturn(expected);

        GanttTaskResponse actual = locationGanttTaskController.updateGanttTask(jwt, 8L, 19L, request);

        assertSame(expected, actual);
        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(locationGanttTaskService).updateLocationTask(42L, 8L, 19L, request);
    }

    @Test
    void deleteGanttTaskDelegatesToServiceForAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("42")
            .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");

        when(authenticatedUserService.resolveAuthenticatedUserId(jwt)).thenReturn(42L);
        when(requestMetadataResolver.resolveClientIp(request)).thenReturn("203.0.113.8");

        locationGanttTaskController.deleteGanttTask(jwt, 8L, 19L, request);

        verify(authenticatedUserService).resolveAuthenticatedUserId(jwt);
        verify(requestMetadataResolver).resolveClientIp(request);
        verify(locationGanttTaskService).deleteLocationTask(42L, 8L, 19L, "203.0.113.8");
    }

    private LocationGanttTaskRequest request(String title) {
        return new LocationGanttTaskRequest(
            title,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-10"),
            "Operations update"
        );
    }

    private GanttTaskResponse response(Long id, String title) {
        return new GanttTaskResponse(
            id,
            title,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-10"),
            "Operations update",
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-03-02T00:00:00Z")
        );
    }
}
