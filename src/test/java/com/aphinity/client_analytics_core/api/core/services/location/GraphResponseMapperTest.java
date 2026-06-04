package com.aphinity.client_analytics_core.api.core.services.location;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphResponseMapperTest {
    @Test
    void returnsCanonicalAllTimePayloadOnly() {
        Graph graph = new Graph();
        graph.setId(10L);
        graph.setName("Water Quality Compliance");
        graph.setCreatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-03-20T00:00:00Z"));
        graph.setData(List.of(Map.of(
            "type", "scatter",
            "name", "HPC",
            "x", List.of("2025-11-01", "2025-12-05", "2026-01-15", "2026-03-10"),
            "y", List.of(4, 5, 7, 9)
        )));

        GraphResponse response = new GraphResponseMapper().toResponse(graph);

        assertEquals(10L, response.id());
        assertEquals("Water Quality Compliance", response.name());
        assertEquals(
            List.of("2025-11-01", "2025-12-05", "2026-01-15", "2026-03-10"),
            response.data().getFirst().get("x")
        );
        assertEquals(List.of(4L, 5L, 7L, 9L), response.data().getFirst().get("y"));
    }
}
