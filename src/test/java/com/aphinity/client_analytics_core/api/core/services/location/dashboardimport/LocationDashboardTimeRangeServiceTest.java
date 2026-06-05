package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.services.location.DashboardGraphMonthRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategyConfig.RangeProfile.TOWERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationDashboardTimeRangeServiceTest {
    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationGraphRepository locationGraphRepository;

    @Mock
    private ServiceEventRepository serviceEventRepository;

    @Mock
    private LocationDashboardImportStrategyRegistry strategyRegistry;

    @Test
    void allTimeResponseProjectionDoesNotAdvanceDerivedGraphUpdatedAt() {
        Location location = new Location();
        location.setId(2L);
        location.setName("Hoag");

        Graph importedGraph = graph(
            5L,
            "Water Quality Conformance",
            Map.of("title", Map.of("text", "Hoag")),
            List.of(Map.of(
                "type", "scatter",
                "name", "HPC",
                "x", List.of("2026-01-01"),
                "y", List.of(100),
                "customdata", List.of(Map.of("sampleCount", 3))
            )),
            Instant.parse("2026-06-05T21:40:12.769052Z")
        );
        Graph derivedGraph = graph(
            1L,
            "Total Number of Samples",
            Map.of("meta", Map.of("aphinityImport", Map.of("derivedGraphId", "total-samples"))),
            List.of(Map.of("type", "pie", "name", "Trace 1", "labels", List.of("fill"), "values", List.of(0))),
            Instant.parse("2026-06-05T22:35:21.632076Z")
        );

        when(locationRepository.findById(2L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdWithGraphDetails(2L))
            .thenReturn(List.of(locationGraph(2L, importedGraph), locationGraph(2L, derivedGraph)));
        when(strategyRegistry.resolve("Hoag")).thenReturn(Optional.of(strategy()));
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(2L))
            .thenReturn(List.of());

        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            Clock.fixed(Instant.parse("2026-06-05T22:35:22.064823225Z"), ZoneOffset.UTC)
        );

        Map<Long, List<Map<String, Object>>> payloads = service.resolveLocationMonthRangePayloads(
            2L,
            DashboardGraphMonthRange.ALL_TIME
        );

        assertFalse(payloads.get(1L).isEmpty());
        assertEquals(
            Instant.parse("2026-06-05T22:35:21.632076Z"),
            derivedGraph.getUpdatedAt()
        );
    }

    private ConfiguredLocationDashboardImportStrategy strategy() {
        return new ConfiguredLocationDashboardImportStrategy(new LocationDashboardImportStrategyConfig(
            "Hoag",
            List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                "hoag",
                "Hoag",
                List.of("Hoag"),
                List.of(),
                true
            )),
            List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                "towers",
                "Towers",
                TOWERS,
                List.of("Towers")
            )),
            List.of(new LocationDashboardImportStrategyConfig.GraphConfig(
                "water-quality",
                "Water Quality Conformance",
                "Hoag",
                WATER_QUALITY_COMPLIANCE,
                "hoag",
                List.of("HPC"),
                Map.of("HPC", "#1f77b4"),
                "scatter"
            )),
            List.of(new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "total-samples",
                "Total Number of Samples",
                null,
                TOTAL_SAMPLES,
                "pie"
            )),
            List.of()
        ));
    }

    private Graph graph(
        Long id,
        String name,
        Map<String, Object> layout,
        List<Map<String, Object>> data,
        Instant updatedAt
    ) {
        Graph graph = new Graph();
        graph.setId(id);
        graph.setName(name);
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(updatedAt);
        graph.setLayout(new LinkedHashMap<>(layout));
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setData(data);
        return graph;
    }

    private LocationGraph locationGraph(Long locationId, Graph graph) {
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(locationId, graph.getId()));
        locationGraph.setGraph(graph);
        return locationGraph;
    }
}
