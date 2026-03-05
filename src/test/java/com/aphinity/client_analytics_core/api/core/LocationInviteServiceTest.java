package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.LocationInvite;
import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;
import com.aphinity.client_analytics_core.api.core.entities.LocationUser;
import com.aphinity.client_analytics_core.api.core.repositories.LocationInviteRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.core.response.LocationInviteResponse;
import com.aphinity.client_analytics_core.api.core.response.LocationResponse;
import com.aphinity.client_analytics_core.api.core.services.AccountRoleService;
import com.aphinity.client_analytics_core.api.core.services.LocationInviteService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationInviteServiceTest {
    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationInviteRepository locationInviteRepository;

    @Mock
    private LocationUserRepository locationUserRepository;

    @Mock
    private AccountRoleService accountRoleService;

    @InjectMocks
    private LocationInviteService locationInviteService;

    @Test
    void getActiveInvitesRejectsUnverifiedUser() {
        AppUser user = unverifiedUser(15L, "unverified@example.com");
        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.getActiveInvites(15L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Account email is not verified", ex.getReason());
        verifyNoInteractions(locationRepository, locationInviteRepository, locationUserRepository, accountRoleService);
    }

    @Test
    void getActiveInvitesNormalizesAuthenticatedEmailBeforeLookup() {
        AppUser user = verifiedUser(15L, "Client@Example.com");
        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));
        when(locationInviteRepository.findByInvitedEmailAndStatusWithLocation(
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(List.of());

        assertEquals(0, locationInviteService.getActiveInvites(15L).size());
        verify(locationInviteRepository).findByInvitedEmailAndStatusWithLocation(
            "client@example.com",
            LocationInviteStatus.PENDING
        );
    }

    @Test
    void createInviteAllowsElevatedUserWithoutExplicitLocationMembership() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());
        when(locationInviteRepository.saveAndFlush(any(LocationInvite.class))).thenAnswer(invocation -> {
            LocationInvite invite = invocation.getArgument(0);
            invite.setId(101L);
            return invite;
        });

        locationInviteService.createInvite(7L, 23L, "client@example.com");

        verify(locationUserRepository).existsByIdLocationIdAndIdUserId(23L, 9L);
        verify(locationUserRepository, never()).existsByIdLocationIdAndIdUserId(23L, 7L);
    }

    @Test
    void createInviteRejectsUnknownInvitedUser() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Invited user not found", ex.getReason());
        verifyNoInteractions(locationInviteRepository);
    }

    @Test
    void createInviteRejectsUnverifiedInvitedUser() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = unverifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Invited account email is not verified", ex.getReason());
        verifyNoInteractions(locationInviteRepository);
    }

    @Test
    void createInviteReturnsConflictWhenRepositoryDetectsConcurrentPendingInvite() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());
        when(locationInviteRepository.saveAndFlush(any(LocationInvite.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"location_invite_one_pending_per_email\""
            ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("An active invite already exists", ex.getReason());
    }

    @Test
    void createInvitePropagatesUnexpectedDataIntegrityViolation() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());
        when(locationInviteRepository.saveAndFlush(any(LocationInvite.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"location_invite_token_hash_key\""
            ));

        assertThrows(DataIntegrityViolationException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );
    }

    @Test
    void acceptInviteCreatesMembershipWhenMissing() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "client@example.com");

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));
        when(locationInviteRepository.save(invite)).thenReturn(invite);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(Optional.empty());
        when(locationUserRepository.save(any(LocationUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        locationInviteService.acceptInvite(9L, 91L);

        assertEquals(LocationInviteStatus.ACCEPTED, invite.getStatus());
        verify(locationInviteRepository).findByIdForUpdate(91L);
        verify(locationInviteRepository, never()).findById(91L);
        verify(locationUserRepository).save(any(LocationUser.class));
    }

    @Test
    void declineInviteRejectsAuthenticatedUserWhenInviteEmailDoesNotMatch() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "different@example.com");

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.declineInvite(9L, 91L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Invite email does not match authenticated account", ex.getReason());
        verify(locationInviteRepository).findByIdForUpdate(91L);
        verify(locationInviteRepository, never()).findById(91L);
        verify(locationInviteRepository, never()).save(any(LocationInvite.class));
    }

    @Test
    void getInviteableLocationsReturnsAllLocationsForElevatedUsers() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location austin = location(11L, "Austin");
        Location denver = location(12L, "Denver");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findAllByOrderByNameAsc()).thenReturn(List.of(austin, denver));

        List<LocationResponse> response = locationInviteService.getInviteableLocations(7L);

        assertEquals(2, response.size());
        assertEquals("Austin", response.get(0).name());
        assertEquals("Denver", response.get(1).name());
        verify(locationRepository).findAllByOrderByNameAsc();
        verifyNoInteractions(locationUserRepository);
    }

    @Test
    void createInviteExpiresExistingPendingInviteBeforeCreatingReplacement() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");
        LocationInvite expiredPendingInvite = pendingInvite(41L, location, inviter, "client@example.com");
        expiredPendingInvite.setExpiresAt(Instant.now().minusSeconds(5));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.of(expiredPendingInvite));
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());
        when(locationInviteRepository.save(any(LocationInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationInviteRepository.saveAndFlush(any(LocationInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        locationInviteService.createInvite(7L, 23L, "client@example.com");

        assertEquals(LocationInviteStatus.EXPIRED, expiredPendingInvite.getStatus());
        verify(locationInviteRepository, times(1)).save(expiredPendingInvite);
        verify(locationInviteRepository, times(1)).saveAndFlush(any(LocationInvite.class));
    }

    @Test
    void createInviteRejectsInviteToOwnAccount() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("partner@example.com")).thenReturn(Optional.of(inviter));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "  PARTNER@EXAMPLE.COM ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot invite your own account", ex.getReason());
        verifyNoInteractions(locationInviteRepository, locationUserRepository);
    }

    @Test
    void createInviteRejectsExistingUnexpiredPendingInvite() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");
        LocationInvite activePendingInvite = pendingInvite(44L, location, inviter, "client@example.com");
        activePendingInvite.setExpiresAt(Instant.now().plusSeconds(60));

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.of(activePendingInvite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("An active invite already exists", ex.getReason());
        verify(locationInviteRepository, never()).saveAndFlush(any(LocationInvite.class));
    }

    @Test
    void createInviteRejectsInvitedUserThatAlreadyHasMembership() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("User already has access to this location", ex.getReason());
        verify(locationInviteRepository, never()).saveAndFlush(any(LocationInvite.class));
    }

    @Test
    void createInviteRejectsInviterWithoutRequiredRole() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, locationInviteRepository, locationUserRepository);
    }

    @Test
    void getInviteableLocationsRejectsInviterWithoutRequiredRole() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.getInviteableLocations(7L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Insufficient permissions", ex.getReason());
        verifyNoInteractions(locationRepository, locationInviteRepository, locationUserRepository);
    }

    @Test
    void getActiveInvitesRejectsBlankAuthenticatedEmail() {
        AppUser user = verifiedUser(15L, "   ");
        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.getActiveInvites(15L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid authenticated user", ex.getReason());
        verifyNoInteractions(locationInviteRepository);
    }

    @Test
    void acceptInviteRejectsNonPendingInvite() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "client@example.com");
        invite.setStatus(LocationInviteStatus.ACCEPTED);

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.acceptInvite(9L, 91L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invite is not pending", ex.getReason());
        verify(locationInviteRepository, never()).save(any(LocationInvite.class));
    }

    @Test
    void acceptInviteExpiresInviteAndRejectsWhenInviteExpired() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "client@example.com");
        invite.setExpiresAt(Instant.now().minusSeconds(1));

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));
        when(locationInviteRepository.save(invite)).thenReturn(invite);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.acceptInvite(9L, 91L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invite expired", ex.getReason());
        assertEquals(LocationInviteStatus.EXPIRED, invite.getStatus());
        verify(locationInviteRepository).save(invite);
        verify(locationUserRepository, never()).save(any(LocationUser.class));
    }

    @Test
    void declineInviteTransitionsPendingInviteToRevoked() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "client@example.com");
        invite.setAcceptedAt(Instant.now().minusSeconds(50));
        invite.setAcceptedUser(inviter);

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));
        when(locationInviteRepository.save(invite)).thenReturn(invite);

        LocationInviteResponse response = locationInviteService.declineInvite(9L, 91L);

        assertEquals(LocationInviteStatus.REVOKED, invite.getStatus());
        assertEquals(LocationInviteStatus.REVOKED, response.status());
        assertNull(invite.getAcceptedAt());
        assertNull(invite.getAcceptedUser());
        verify(locationInviteRepository).save(invite);
    }

    @Test
    void createInviteReturnsConflictWhenConstraintNameExistsInCauseChain() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());

        ConstraintViolationException cause = new ConstraintViolationException(
            "duplicate",
            new SQLException("duplicate"),
            "location_invite_one_pending_per_email"
        );
        when(locationInviteRepository.saveAndFlush(any(LocationInvite.class)))
            .thenThrow(new DataIntegrityViolationException("insert failed", cause));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("An active invite already exists", ex.getReason());
    }

    @Test
    void createInviteFailsAfterTokenHashCollisionRetries() {
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        Location location = location(23L, "Austin");
        LocationInvite collidingInvite = pendingInvite(51L, location, inviter, "client@example.com");

        when(appUserRepository.findById(7L)).thenReturn(Optional.of(inviter));
        when(accountRoleService.isPartnerOrAdmin(inviter)).thenReturn(true);
        when(locationRepository.findById(23L)).thenReturn(Optional.of(location));
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByLocationIdAndInvitedEmailAndStatus(
            23L,
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(locationUserRepository.existsByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(false);
        when(locationInviteRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(collidingInvite));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationInviteService.createInvite(7L, 23L, "client@example.com")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Unable to issue invite", ex.getReason());
        verify(locationInviteRepository, never()).saveAndFlush(any(LocationInvite.class));
    }

    @Test
    void getActiveInvitesExpiresExpiredEntriesAndReturnsOnlyActiveInvites() {
        AppUser user = verifiedUser(15L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");

        LocationInvite expiredInvite = pendingInvite(51L, location, inviter, "client@example.com");
        expiredInvite.setExpiresAt(Instant.now().minusSeconds(5));
        LocationInvite activeInvite = pendingInvite(52L, location, inviter, "client@example.com");
        activeInvite.setExpiresAt(Instant.now().plusSeconds(300));

        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));
        when(locationInviteRepository.findByInvitedEmailAndStatusWithLocation(
            "client@example.com",
            LocationInviteStatus.PENDING
        )).thenReturn(List.of(expiredInvite, activeInvite));
        when(locationInviteRepository.save(any(LocationInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<LocationInviteResponse> responses = locationInviteService.getActiveInvites(15L);

        assertEquals(1, responses.size());
        assertEquals(activeInvite.getId(), responses.getFirst().id());
        assertEquals(LocationInviteStatus.EXPIRED, expiredInvite.getStatus());
        verify(locationInviteRepository).save(expiredInvite);
    }

    @Test
    void acceptInviteSkipsMembershipCreationWhenMembershipAlreadyExists() {
        AppUser invitedUser = verifiedUser(9L, "client@example.com");
        AppUser inviter = verifiedUser(7L, "partner@example.com");
        Location location = location(23L, "Austin");
        LocationInvite invite = pendingInvite(91L, location, inviter, "client@example.com");
        LocationUser existingMembership = new LocationUser();

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(invitedUser));
        when(locationInviteRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(invite));
        when(locationInviteRepository.save(invite)).thenReturn(invite);
        when(locationUserRepository.findByIdLocationIdAndIdUserId(23L, 9L)).thenReturn(Optional.of(existingMembership));

        locationInviteService.acceptInvite(9L, 91L);

        verify(locationUserRepository).findByIdLocationIdAndIdUserId(23L, 9L);
        verify(locationUserRepository, never()).save(any(LocationUser.class));
    }

    private AppUser verifiedUser(Long id, String email) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setEmailVerifiedAt(Instant.now());
        return user;
    }

    private AppUser unverifiedUser(Long id, String email) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setEmailVerifiedAt(null);
        return user;
    }

    private Location location(Long id, String name) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        location.setCreatedAt(now);
        location.setUpdatedAt(now);
        location.setSectionLayout(Map.of("sections", List.of()));
        return location;
    }

    private LocationInvite pendingInvite(Long id, Location location, AppUser invitedBy, String invitedEmail) {
        Instant now = Instant.now();
        LocationInvite invite = new LocationInvite();
        invite.setId(id);
        invite.setLocation(location);
        invite.setInvitedBy(invitedBy);
        invite.setInvitedEmail(invitedEmail);
        invite.setStatus(LocationInviteStatus.PENDING);
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusSeconds(3600));
        invite.setTokenHash("hash-" + id);
        return invite;
    }

}
