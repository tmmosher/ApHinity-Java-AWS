package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.Graph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GraphRepository extends JpaRepository<Graph, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select graph from Graph graph
        join graph.locationGraphs locationGraph
        where locationGraph.id.locationId = :locationId
          and graph.id in :graphIds
        order by graph.id asc
        """)
    List<Graph> findByLocationIdAndGraphIdInForUpdate(
        @Param("locationId") Long locationId,
        @Param("graphIds") Collection<Long> graphIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select graph from Graph graph
        join graph.locationGraphs locationGraph
        where locationGraph.id.locationId = :locationId
          and graph.id = :graphId
        """)
    Optional<Graph> findByLocationIdAndGraphIdForUpdate(
        @Param("locationId") Long locationId,
        @Param("graphId") Long graphId
    );
}
