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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LocationGraphPipelineWebMvcTest.JwtArgumentResolverConfig.class)
class LocationThumbnailWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;

    @MockitoBean
    private AsyncLogService asyncLogService;

    @Test
    void postsThumbnailMultipartToControllerAndReturnsUpdatedLocation() throws Exception {
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(42L);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "thumbnail.png",
            "image/png",
            new byte[] {4, 5, 6}
        );
        when(locationService.updateLocationThumbnail(eq(42L), eq(8L), any(MultipartFile.class))).thenReturn(
            new com.aphinity.client_analytics_core.api.core.response.location.LocationResponse(
                8L,
                "Dallas",
                null,
                null,
                java.util.Map.of(),
                null,
                null,
                true
            )
        );

        mockMvc.perform(
                multipart("/core/locations/{locationId}/thumbnail", 8L)
                    .file(file)
                    .with(csrf().asHeader())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(8))
            .andExpect(jsonPath("$.thumbnailAvailable").value(true));

        verify(authenticatedUserService).resolveAuthenticatedUserId(nullable(Jwt.class));
        verify(locationService).updateLocationThumbnail(eq(42L), eq(8L), any(MultipartFile.class));
    }

    @Test
    void getsThumbnailAsWebpBytes() throws Exception {
        when(authenticatedUserService.resolveAuthenticatedUserId(nullable(Jwt.class))).thenReturn(42L);
        byte[] thumbnail = new byte[] {7, 8, 9};
        when(locationService.getAccessibleLocationThumbnail(42L, 8L)).thenReturn(thumbnail);

        mockMvc.perform(get("/core/locations/{locationId}/thumbnail", 8L))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/webp"))
            .andExpect(content().bytes(thumbnail));

        verify(authenticatedUserService).resolveAuthenticatedUserId(nullable(Jwt.class));
        verify(locationService).getAccessibleLocationThumbnail(42L, 8L);
    }
}
