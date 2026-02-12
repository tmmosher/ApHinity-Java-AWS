package com.aphinity.client_analytics_core.api.core.services;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.LocationInvite;
import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;
import com.aphinity.client_analytics_core.api.core.entities.LocationMemberRole;
import com.aphinity.client_analytics_core.api.core.entities.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.LocationUserId;
import com.aphinity.client_analytics_core.api.core.repositories.LocationInviteRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.LocationInviteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Manages invitation lifecycle for granting users access to locations.
 */
@Service
public class LocationInviteService {
    private static final Duration INVITE_EXPIRATION = Duration.ofDays(7);

    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationInviteRepository locationInviteRepository;
    private final LocationUserRepository locationUserRepository;
    private final AccountRoleService accountRoleService;
    private final SecureRandom secureRandom = new SecureRandom();

    public LocationInviteService(
        AppUserRepository appUserRepository,
        LocationRepository locationRepository,
        LocationInviteRepository locationInviteRepository,
        LocationUserRepository locationUserRepository,
        AccountRoleService accountRoleService
    ) {
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.locationInviteRepository = locationInviteRepository;
        this.locationUserRepository = locationUserRepository;
        this.accountRoleService = accountRoleService;
    }

    /**
     * Creates a pending invite for a location/email pair.
     *
     * @param userId authenticated inviter id
     * @param locationId location to invite into
     * @param invitedEmail recipient email
     * @return persisted invite response
     */
    @Transactional
    public LocationInviteResponse createInvite(Long userId, Long locationId, String invitedEmail) {
        AppUser inviter = requireUser(userId);
        requirePartnerOrAdmin(inviter);

        Location location = locationRepository.findById(locationId).orElseThrow(this::locationNotFound);
        String normalizedInvitedEmail = normalizeEmail(invitedEmail);

        if (inviter.getEmail().equalsIgnoreCase(normalizedInvitedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite your own account");
        }

        // Prevent duplicate active invites for the same location/email tuple.
        Optional<LocationInvite> existingPending = locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            locationId,
            normalizedInvitedEmail,
            LocationInviteStatus.PENDING
        );
        if (existingPending.isPresent()) {
            LocationInvite invite = existingPending.get();
            if (!isExpired(invite)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An active invite already exists");
            }
            expireInvite(invite);
        }

        // If the invited account already has membership, creating an invite would be redundant.
        appUserRepository.findByEmail(normalizedInvitedEmail)
            .ifPresent(existingUser -> {
                if (locationUserRepository.existsByIdLocationIdAndIdUserId(locationId, existingUser.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has access to this location");
                }
            });

        LocationInvite invite = new LocationInvite();
        invite.setLocation(location);
        invite.setInvitedEmail(normalizedInvitedEmail);
        invite.setInvitedBy(inviter);
        invite.setExpiresAt(Instant.now().plus(INVITE_EXPIRATION));
        invite.setStatus(LocationInviteStatus.PENDING);
        invite.setTokenHash(newUniqueTokenHash());

        LocationInvite persisted = locationInviteRepository.save(invite);
        return toResponse(persisted);
    }

    /**
     * Lists non-expired pending invites for the authenticated user's email.
     *
     * @param userId authenticated user id
     * @return active invites
     */
    @Transactional
    public List<LocationInviteResponse> getActiveInvites(Long userId) {
        AppUser user = requireUser(userId);
        Instant now = Instant.now();

        List<LocationInviteResponse> responses = new ArrayList<>();
        List<LocationInvite> invites = locationInviteRepository.findByInvitedEmailAndStatusWithLocation(
            user.getEmail(),
            LocationInviteStatus.PENDING
        );
        for (LocationInvite invite : invites) {
            if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now)) {
                expireInvite(invite);
                continue;
            }
            responses.add(toResponse(invite));
        }
        return List.copyOf(responses);
    }

    /**
     * Accepts an invite and ensures the user gains location membership.
     *
     * @param userId authenticated user id
     * @param inviteId invite id
     * @return updated invite response
     */
    @Transactional
    public LocationInviteResponse acceptInvite(Long userId, Long inviteId) {
        AppUser user = requireUser(userId);
        LocationInvite invite = locationInviteRepository.findById(inviteId).orElseThrow(this::inviteNotFound);

        validateInviteOwnership(user, invite);
        ensurePendingInvite(invite);
        if (isExpired(invite)) {
            expireInvite(invite);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite expired");
        }

        invite.setStatus(LocationInviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedUser(user);
        invite.setRevokedAt(null);
        LocationInvite persistedInvite = locationInviteRepository.save(invite);

        // Membership creation is idempotent to tolerate repeated accept calls or races.
        ensureMembershipForAcceptedInvite(user, invite.getLocation());
        return toResponse(persistedInvite);
    }

    /**
     * Declines an invite by transitioning it to revoked state.
     *
     * @param userId authenticated user id
     * @param inviteId invite id
     * @return updated invite response
     */
    @Transactional
    public LocationInviteResponse declineInvite(Long userId, Long inviteId) {
        AppUser user = requireUser(userId);
        LocationInvite invite = locationInviteRepository.findById(inviteId).orElseThrow(this::inviteNotFound);

        validateInviteOwnership(user, invite);
        ensurePendingInvite(invite);
        if (isExpired(invite)) {
            expireInvite(invite);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite expired");
        }

        invite.setStatus(LocationInviteStatus.REVOKED);
        invite.setRevokedAt(Instant.now());
        invite.setAcceptedAt(null);
        invite.setAcceptedUser(null);

        LocationInvite persistedInvite = locationInviteRepository.save(invite);
        return toResponse(persistedInvite);
    }

    /**
     * Ensures accepted invites result in membership, creating one when needed.
     */
    private void ensureMembershipForAcceptedInvite(AppUser user, Location location) {
        Long userId = user.getId();
        Long locationId = location.getId();
        Optional<LocationUser> existingMembership = locationUserRepository.findByIdLocationIdAndIdUserId(locationId, userId);
        if (existingMembership.isPresent()) {
            return;
        }

        LocationUser membership = new LocationUser();
        membership.setId(new LocationUserId(locationId, userId));
        membership.setLocation(location);
        membership.setUser(user);
        membership.setUserRole(LocationMemberRole.CLIENT);
        locationUserRepository.save(membership);
    }

    /**
     * Validates that invite recipient email matches the authenticated account.
     */
    private void validateInviteOwnership(AppUser user, LocationInvite invite) {
        String userEmail = user.getEmail();
        String invitedEmail = invite.getInvitedEmail();
        if (userEmail == null || invitedEmail == null || !userEmail.equalsIgnoreCase(invitedEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite email does not match authenticated account");
        }
    }

    /**
     * Ensures an invite can still be acted upon.
     */
    private void ensurePendingInvite(LocationInvite invite) {
        if (invite.getStatus() != LocationInviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite is not pending");
        }
    }

    /**
     * Determines whether invite expiration has passed.
     */
    private boolean isExpired(LocationInvite invite) {
        Instant expiresAt = invite.getExpiresAt();
        if (expiresAt == null) {
            return false;
        }
        return !expiresAt.isAfter(Instant.now());
    }

    /**
     * Marks an invite as expired.
     */
    private void expireInvite(LocationInvite invite) {
        invite.setStatus(LocationInviteStatus.EXPIRED);
        invite.setRevokedAt(null);
        locationInviteRepository.save(invite);
    }

    /**
     * Maps invite entities into API response shape.
     */
    private LocationInviteResponse toResponse(LocationInvite invite) {
        AppUser invitedBy = invite.getInvitedBy();
        AppUser acceptedUser = invite.getAcceptedUser();
        Location location = invite.getLocation();
        return new LocationInviteResponse(
            invite.getId(),
            location == null ? null : location.getId(),
            location == null ? null : location.getName(),
            invite.getInvitedEmail(),
            invitedBy == null ? null : invitedBy.getId(),
            invite.getStatus(),
            invite.getExpiresAt(),
            invite.getCreatedAt(),
            invite.getAcceptedAt(),
            acceptedUser == null ? null : acceptedUser.getId(),
            invite.getRevokedAt()
        );
    }

    /**
     * Trims and lowercases invited email values.
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invited email is required");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invited email is required");
        }
        return normalized;
    }

    /**
     * Generates a token hash with retry-on-collision semantics.
     */
    private String newUniqueTokenHash() {
        for (int attempts = 0; attempts < 5; attempts++) {
            String candidate = hashToken(generateToken());
            if (locationInviteRepository.findByTokenHash(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to issue invite");
    }

    /**
     * Creates random raw invite token bytes encoded as hex.
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashes invite tokens before persistence.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    /**
     * Loads authenticated user or throws a standard unauthorized error.
     */
    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId).orElseThrow(this::invalidAuthenticatedUser);
    }

    /**
     * Enforces inviter role requirements.
     */
    private void requirePartnerOrAdmin(AppUser user) {
        if (!accountRoleService.isPartnerOrAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    private ResponseStatusException invalidAuthenticatedUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
    }

    private ResponseStatusException locationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
    }

    private ResponseStatusException inviteNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found");
    }
}
