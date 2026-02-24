package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.LocationUserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationUserRepository extends JpaRepository<LocationUser, LocationUserId> {
    List<LocationUser> findByIdLocationId(Long locationId);

    List<LocationUser> findByIdUserId(Long userId);

    boolean existsByIdLocationIdAndIdUserId(Long locationId, Long userId);

    @Query("""
        select membership from LocationUser membership
        join fetch membership.location
        where membership.id.userId = :userId
        """)
    List<LocationUser> findByUserIdWithLocation(@Param("userId") Long userId);

    @Query("""
        select membership from LocationUser membership
        join fetch membership.user
        where membership.id.locationId = :locationId
        order by membership.createdAt asc
        """)
    List<LocationUser> findByLocationIdWithUser(@Param("locationId") Long locationId);

    Optional<LocationUser> findByIdLocationIdAndIdUserId(Long locationId, Long userId);
}
