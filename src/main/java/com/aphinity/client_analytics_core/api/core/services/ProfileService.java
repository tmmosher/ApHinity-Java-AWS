package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.services.AuthService;
import com.aphinity.client_analytics_core.api.core.response.AccountRole;
import com.aphinity.client_analytics_core.api.core.response.ProfileResponse;
import com.aphinity.client_analytics_core.api.security.PasswordPolicyValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Handles authenticated user profile reads and updates.
 */
@Service
public class ProfileService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRoleService accountRoleService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthService authService;

    public ProfileService(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        AccountRoleService accountRoleService,
        PasswordPolicyValidator passwordPolicyValidator,
        AuthService authService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountRoleService = accountRoleService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.authService = authService;
    }

    /**
     * Returns profile data for the authenticated user.
     *
     * @param userId authenticated user id
     * @return profile payload
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(this::invalidAuthenticatedUser);

        return toProfileResponse(user);
    }

    /**
     * Updates display name and email for the authenticated user.
     *
     * @param userId authenticated user id
     * @param name new display name
     * @param email new email address
     * @return updated profile payload
     */
    @Transactional
    public ProfileResponse updateProfile(Long userId, String name, String email) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);
        AccountRole accountRole = accountRoleService.resolveAccountRole(user);

        String normalizedName = normalizeName(name).strip();
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(normalizedEmail);

        if (emailChanged) {
            rejectEmailChangeForRole(accountRole);
            // Fast-path duplicate detection for clearer conflict errors before flush.
            appUserRepository.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw userAlreadyExists();
                });
        }

        user.setName(normalizedName);
        if (emailChanged) {
            user.setEmail(normalizedEmail);
            user.setEmailVerifiedAt(null);
        }

        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw userAlreadyExists();
        }

        if (emailChanged) {
            authService.issueAndSendVerificationCode(user.getId(), normalizedEmail);
        }

        return toProfileResponse(user);
    }

    /**
     * Changes the authenticated user's password after validating current credentials.
     *
     * @param userId authenticated user id
     * @param currentPassword current raw password
     * @param newPassword new raw password
     */
    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(this::invalidAuthenticatedUser);
        requireVerified(user);

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password change unavailable for this account");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        passwordPolicyValidator.validateOrThrow(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }

    /**
     * Normalizes names while preserving legacy behavior for null values.
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name;
    }

    /**
     * Maps user entities into API response shape.
     */
    private ProfileResponse toProfileResponse(AppUser user) {
        return new ProfileResponse(
            normalizeName(user.getName()),
            user.getEmail(),
            user.getEmailVerifiedAt() != null,
            accountRoleService.resolveAccountRole(user)
        );
    }

    private ResponseStatusException userAlreadyExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private void rejectEmailChangeForRole(AccountRole accountRole) {
        if (accountRole == AccountRole.ADMIN || accountRole == AccountRole.PARTNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email changes are not allowed for this role");
        }
    }

    private void requireVerified(AppUser user) {
        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email is not verified");
        }
    }
}
