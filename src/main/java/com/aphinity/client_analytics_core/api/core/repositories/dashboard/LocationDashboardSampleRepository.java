package com.aphinity.client_analytics_core.api.core.repositories.dashboard;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationDashboardSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LocationDashboardSampleRepository extends JpaRepository<LocationDashboardSample, Long> {
    List<LocationDashboardSample> findByLocation_IdOrderByObservedDateAscIdAsc(Long locationId);

    @Query("""
        select sample
        from LocationDashboardSample sample
        where sample.location.id = :locationId
          and sample.observedDate >= :startDate
        order by sample.observedDate asc, sample.id asc
        """)
    List<LocationDashboardSample> findByLocationIdAndObservedDateOnOrAfter(
        @Param("locationId") Long locationId,
        @Param("startDate") LocalDate startDate
    );

    @Modifying
    @Query("delete from LocationDashboardSample sample where sample.location.id = :locationId")
    int deleteByLocationId(@Param("locationId") Long locationId);
}
