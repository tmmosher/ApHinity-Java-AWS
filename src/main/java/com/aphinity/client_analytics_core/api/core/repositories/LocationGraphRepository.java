package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.LocationGraphId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationGraphRepository extends JpaRepository<LocationGraph, LocationGraphId> {
    List<LocationGraph> findByIdLocationId(Long locationId);

    @Query("""
        select locationGraph from LocationGraph locationGraph
        join fetch locationGraph.graph
        where locationGraph.id.locationId = :locationId
        order by locationGraph.createdAt asc
        """)
    List<LocationGraph> findByLocationIdWithGraph(@Param("locationId") Long locationId);

    List<LocationGraph> findByIdGraphId(Long graphId);

    boolean existsByIdLocationIdAndIdGraphId(Long locationId, Long graphId);
}
