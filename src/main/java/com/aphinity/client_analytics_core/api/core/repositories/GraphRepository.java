package com.aphinity.client_analytics_core.api.core.repositories;

import com.aphinity.client_analytics_core.api.core.entities.Graph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphRepository extends JpaRepository<Graph, Long> {
    List<Graph> findByOwner_Id(Long ownerId);
}
