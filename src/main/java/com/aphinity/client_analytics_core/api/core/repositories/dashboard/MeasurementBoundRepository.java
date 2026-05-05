package com.aphinity.client_analytics_core.api.core.repositories.dashboard;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeasurementBoundRepository extends JpaRepository<MeasurementBound, Long> {
    @Query("""
        select measurementBound
        from MeasurementBound measurementBound
        where measurementBound.id in (
            select locationMeasurement.id.measurementId
            from LocationMeasurement locationMeasurement
            where locationMeasurement.id.locationId = :locationId
        )
        order by measurementBound.id asc
        """)
    List<MeasurementBound> findByLocationId(@Param("locationId") Long locationId);
}
