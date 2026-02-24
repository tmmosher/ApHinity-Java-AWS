package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.LocationInvite;
import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationInviteRepository extends JpaRepository<LocationInvite, Long> {
    Optional<LocationInvite> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from LocationInvite invite where invite.id = :inviteId")
    Optional<LocationInvite> findByIdForUpdate(@Param("inviteId") Long inviteId);

    List<LocationInvite> findByLocation_IdAndStatus(Long locationId, LocationInviteStatus status);

    @Query("""
        select invite from LocationInvite invite
        where invite.location.id = :locationId
          and invite.invitedEmail = :invitedEmail
          and invite.status = :status
        """)
    Optional<LocationInvite> findByLocationIdAndInvitedEmailAndStatus(
        @Param("locationId") Long locationId,
        @Param("invitedEmail") String invitedEmail,
        @Param("status") LocationInviteStatus status
    );

    @Query("""
        select invite from LocationInvite invite
        join fetch invite.location
        where invite.invitedEmail = :invitedEmail
          and invite.status = :status
        order by invite.createdAt desc
        """)
    List<LocationInvite> findByInvitedEmailAndStatusWithLocation(
        @Param("invitedEmail") String invitedEmail,
        @Param("status") LocationInviteStatus status
    );
}
