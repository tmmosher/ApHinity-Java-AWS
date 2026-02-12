package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.LocationInvite;
import com.aphinity.client_analytics_core.api.core.entities.LocationInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationInviteRepository extends JpaRepository<LocationInvite, Long> {
    Optional<LocationInvite> findByTokenHash(String tokenHash);

    List<LocationInvite> findByLocation_IdAndStatus(Long locationId, LocationInviteStatus status);

    @Query("""
        select invite from LocationInvite invite
        where invite.location.id = :locationId
          and lower(invite.invitedEmail) = lower(:invitedEmail)
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
        where lower(invite.invitedEmail) = lower(:invitedEmail)
          and invite.status = :status
        order by invite.createdAt desc
        """)
    List<LocationInvite> findByInvitedEmailAndStatusWithLocation(
        @Param("invitedEmail") String invitedEmail,
        @Param("status") LocationInviteStatus status
    );
}
