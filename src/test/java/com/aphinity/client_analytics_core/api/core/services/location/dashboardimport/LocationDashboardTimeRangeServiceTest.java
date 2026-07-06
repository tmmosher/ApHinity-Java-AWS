package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Mock
    private LocationDashboardSamplePersistenceService samplePersistenceService;

    @Test
    void finiteRangePayloadResolutionSkipsMissingConfiguredGraphs() {
        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC)
        );
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");
        Graph graph = new Graph();
        graph.setId(101L);
        graph.setName("Water Quality Conformance");
        graph.setLayout(Map.of("title", Map.of("text", "Newport Beach")));
        graph.setData(List.of(Map.of("type", "scatter", "x", List.of(), "y", List.of())));
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(42L, 101L));
        locationGraph.setGraph(graph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdWithGraphDetails(42L)).thenReturn(List.of(locationGraph));
        when(strategyRegistry.resolve("Hoag Hospital")).thenReturn(Optional.of(strategyWithMissingGraph()));

        assertDoesNotThrow(() ->
            service.resolveLocationMonthRangePayloads(42L, new DashboardGraphMonthRange(3))
        );
    }

    private LocationDashboardImportStrategy strategyWithMissingGraph() {
        return new LocationDashboardImportStrategy() {
            @Override
            public String locationName() {
                return "Hoag Hospital";
            }

            @Override
            public List<LocationDashboardImportStrategyConfig.GraphConfig> graphDefinitions() {
                return List.of(
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "newport-water-quality",
                        "Water Quality Conformance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        null,
                        List.of(),
                        Map.of(),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "irvine-water-quality",
                        "Water Quality Conformance",
                        "Irvine",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        null,
                        List.of(),
                        Map.of(),
                        "scatter"
                    )
                );
            }

            @Override
            public List<LocationDashboardImportStrategyConfig.DerivedGraphConfig> derivedGraphDefinitions() {
                return List.of();
            }

            @Override
            public LocationDashboardImportComputation computeImport(
                LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
                List<MeasurementBound> measurementBounds
            ) {
                return new LocationDashboardImportComputation(List.of(), List.of(), List.of(), List.of());
            }
        };
    }
}
