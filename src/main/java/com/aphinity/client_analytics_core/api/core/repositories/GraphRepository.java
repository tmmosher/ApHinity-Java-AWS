package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.Graph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GraphRepository extends JpaRepository<Graph, Long> {
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
}
