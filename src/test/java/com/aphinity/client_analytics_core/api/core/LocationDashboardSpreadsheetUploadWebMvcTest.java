package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.controllers.location.LocationController;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardSpreadsheetUploadResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LocationGraphPipelineWebMvcTest.JwtArgumentResolverConfig.class)
class LocationDashboardSpreadsheetUploadWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;

    @Test
    void postsDashboardSpreadsheetMultipartToControllerAndReturnsUpdatedGraphs() throws Exception {
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(42L);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {4, 5, 6}
        );
        when(locationService.uploadLocationDashboardSpreadsheet(eq(42L), eq(8L), any(MultipartFile.class), eq(false), nullable(Integer.class)))
            .thenReturn(new LocationDashboardSpreadsheetUploadResponse(
                List.of(new GraphResponse(
                    18L,
                    "Water Quality Compliance",
                    List.of(Map.of("type", "scatter", "name", "HPC", "x", List.of("2025-08-01"), "y", List.of(50.0d))),
                    Map.of("meta", Map.of("aphinityImport", Map.of("graphId", "graph-1"))),
                    Map.of(),
                    Map.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-02T00:00:00Z")
                )),
                List.of()
            ));

        mockMvc.perform(
                multipart("/core/locations/{locationId}/dashboard/spreadsheet-upload", 8L)
                    .file(file)
                    .with(csrf().asHeader())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graphs[0].id").value(18L))
            .andExpect(jsonPath("$.graphs[0].name").value("Water Quality Compliance"))
            .andExpect(jsonPath("$.correctiveActions.length()").value(0));

        verify(authenticatedUserService).resolveAuthenticatedUserId(nullable(Jwt.class));
        verify(locationService).uploadLocationDashboardSpreadsheet(eq(42L), eq(8L), any(MultipartFile.class), eq(false), nullable(Integer.class));
    }

    @Test
    void postsDashboardSpreadsheetMultipartSurfacesClientErrorsWithoutStackTraceNoise() throws Exception {
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(42L);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {4, 5, 6}
        );
        when(locationService.uploadLocationDashboardSpreadsheet(eq(42L), eq(8L), any(MultipartFile.class), eq(false), nullable(Integer.class)))
            .thenThrow(new ApiClientException(
                BAD_REQUEST,
                "location_dashboard_file_invalid",
                "Dashboard spreadsheet could not be parsed."
            ));

        mockMvc.perform(
                multipart("/core/locations/{locationId}/dashboard/spreadsheet-upload", 8L)
                    .file(file)
                    .with(csrf().asHeader())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("location_dashboard_file_invalid"))
            .andExpect(jsonPath("$.message").value("Dashboard spreadsheet could not be parsed."));

        verify(authenticatedUserService).resolveAuthenticatedUserId(nullable(Jwt.class));
        verify(locationService).uploadLocationDashboardSpreadsheet(eq(42L), eq(8L), any(MultipartFile.class), eq(false), nullable(Integer.class));
    }
}
