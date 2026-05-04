package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.controllers.location.LocationController;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationService;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @MockitoBean
    private AsyncLogService asyncLogService;

    @Test
    void postsDashboardSpreadsheetMultipartToControllerAndReturnsNoContent() throws Exception {
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(42L);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {4, 5, 6}
        );

        mockMvc.perform(
                multipart("/core/locations/{locationId}/dashboard/spreadsheet-upload", 8L)
                    .file(file)
                    .with(csrf().asHeader())
            )
            .andExpect(status().isNoContent());

        verify(authenticatedUserService).resolveAuthenticatedUserId(nullable(Jwt.class));
        verify(locationService).uploadLocationDashboardSpreadsheet(eq(42L), eq(8L), any(MultipartFile.class));
    }
}
