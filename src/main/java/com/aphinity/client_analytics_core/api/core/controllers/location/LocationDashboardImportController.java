package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.response.dashboard.LocationDashboardSpreadsheetUploadResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationDashboardUploadService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP boundary for location dashboard imports. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationDashboardImportController {
    private final LocationDashboardUploadService service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationDashboardImportController(
        LocationDashboardUploadService service, AuthenticatedUserService authenticatedUserService
    ) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping(path = "/locations/{locationId}/dashboard/spreadsheet-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LocationDashboardSpreadsheetUploadResponse upload(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean persistSamples,
        @RequestParam(required = false) Integer monthRange
    ) {
        return service.upload(
            authenticatedUserService.resolveAuthenticatedUserId(jwt), locationId, file, persistSamples, monthRange
        );
    }
}
