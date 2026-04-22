package com.aphinity.client_analytics_core.api.core.repositories.location;

import com.aphinity.client_analytics_core.api.core.entities.location.UserSubscriptionToLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSubscriptionToLocationRepository extends JpaRepository<UserSubscriptionToLocation, Long> {
    @Query("""
        select subscription
        from UserSubscriptionToLocation subscription
        where subscription.location.id = :locationId
          and subscription.userEmail.id = :userId
        """)
    Optional<UserSubscriptionToLocation> findByLocationIdAndUserId(
        @Param("locationId") Long locationId,
        @Param("userId") Long userId
    );

    @Query("""
        select case when count(subscription) > 0 then true else false end
        from UserSubscriptionToLocation subscription
        where subscription.location.id = :locationId
          and subscription.userEmail.id = :userId
        """)
    boolean existsByLocationIdAndUserId(
        @Param("locationId") Long locationId,
        @Param("userId") Long userId
    );
}
