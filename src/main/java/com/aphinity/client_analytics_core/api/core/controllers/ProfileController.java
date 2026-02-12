package com.aphinity.client_analytics_core.api.core.controllers;

import com.aphinity.client_analytics_core.api.core.requests.ProfilePasswordUpdateRequest;
import com.aphinity.client_analytics_core.api.core.requests.ProfileUpdateRequest;
import com.aphinity.client_analytics_core.api.core.response.ProfileResponse;
import com.aphinity.client_analytics_core.api.core.services.AuthenticatedUserService;
import com.aphinity.client_analytics_core.api.core.services.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Locale;

/**
 * Authenticated profile endpoints.
 */
@RestController
@RequestMapping({"/core", "/api/core"})
public class ProfileController {
    private final ProfileService profileService;
    private final AuthenticatedUserService authenticatedUserService;

    public ProfileController(ProfileService profileService, AuthenticatedUserService authenticatedUserService) {
        this.profileService = profileService;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Returns profile details for the authenticated user.
     *
     * @param jwt authenticated principal JWT
     * @return profile payload
     */
    @GetMapping("/profile")
    public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getProfile(authenticatedUserService.resolveAuthenticatedUserId(jwt));
    }

    /**
     * Updates profile name and email.
     *
     * @param jwt authenticated principal JWT
     * @param request validated profile update payload
     * @return updated profile payload
     */
    @PutMapping("/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.updateProfile(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            request.name().strip(),
            request.email().strip().toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Changes the current user's password.
     *
     * @param jwt authenticated principal JWT
     * @param request validated password update payload
     */
    @PutMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ProfilePasswordUpdateRequest request
    ) {
        profileService.updatePassword(
            authenticatedUserService.resolveAuthenticatedUserId(jwt),
            request.currentPassword().strip(),
            request.newPassword().strip()
        );
    }
}
