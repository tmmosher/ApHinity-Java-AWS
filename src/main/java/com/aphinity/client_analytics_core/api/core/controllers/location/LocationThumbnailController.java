package com.aphinity.client_analytics_core.api.core.controllers.location;

import com.aphinity.client_analytics_core.api.core.response.location.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.location.LocationThumbnailService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP boundary for location thumbnail assets. */
@RestController
@RequestMapping({"/core", "/api/core"})
public class LocationThumbnailController {
    private final LocationThumbnailService service;
    private final AuthenticatedUserService authenticatedUserService;

    public LocationThumbnailController(LocationThumbnailService service, AuthenticatedUserService authenticatedUserService) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping(path = "/locations/{locationId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LocationResponse update(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId, @RequestParam("file") MultipartFile file
    ) {
        return service.updateThumbnail(userId(jwt), locationId, file);
    }

    @GetMapping(value = "/locations/{locationId}/thumbnail", produces = "image/webp")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long locationId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/webp"))
            .body(service.getThumbnail(userId(jwt), locationId));
    }

    private Long userId(Jwt jwt) { return authenticatedUserService.resolveAuthenticatedUserId(jwt); }
}
