package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.Location;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findAllByOrderByNameAsc();

    Optional<Location> findByName(String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Location l set l.updatedAt = :updatedAt where l.id = :locationId")
    int touchUpdatedAt(@Param("locationId") Long locationId, @Param("updatedAt") Instant updatedAt);
}
