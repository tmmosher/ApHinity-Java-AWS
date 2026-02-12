package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.LocationGraphId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationGraphRepository extends JpaRepository<LocationGraph, LocationGraphId> {
    List<LocationGraph> findByIdLocationId(Long locationId);

    List<LocationGraph> findByIdGraphId(Long graphId);

    boolean existsByIdLocationIdAndIdGraphId(Long locationId, Long graphId);
}
