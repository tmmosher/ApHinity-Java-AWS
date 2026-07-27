package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.readData;
import static com.aphinity.client_analytics_core.api.core.plotly.GraphRelationalPayloadMapper.writeData;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
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
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardCorrectiveActionService correctiveActionService =
            new LocationDashboardCorrectiveActionService(serviceEventRepository, clock, strategyRegistry);
        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            new LocationDashboardCache(),
            new LocationDashboardGraphMatcher(),
            new LocationDashboardHistoricalDataAssembler(correctiveActionService),
            clock
        );
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");
        Graph graph = new Graph();
        graph.setId(101L);
        graph.setName("Water Quality Conformance");
        graph.setLayout(Map.of("title", Map.of("text", "Newport Beach")));
        writeData(graph, List.of(Map.of("type", "scatter", "x", List.of(), "y", List.of())));
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(42L, 101L));
        locationGraph.setGraph(graph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdWithGraphDetails(42L)).thenReturn(List.of(locationGraph));

        assertDoesNotThrow(() ->
            service.resolveLocationMonthRangePayloads(42L, new DashboardGraphMonthRange(3))
        );
    }

    @Test
    void finiteRangeProjectionReusesCachedGraphPayloadUntilInvalidated() {
        LocationDashboardCache cache = new LocationDashboardCache();
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardCorrectiveActionService correctiveActionService =
            new LocationDashboardCorrectiveActionService(serviceEventRepository, clock, strategyRegistry);
        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            cache,
            new LocationDashboardGraphMatcher(),
            new LocationDashboardHistoricalDataAssembler(correctiveActionService),
            clock
        );
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");
        Graph graph = new Graph();
        graph.setId(101L);
        graph.setName("Water Quality Conformance");
        writeData(graph, List.of(Map.of(
            "type", "bar",
            "orientation", "v",
            "x", List.of("first"),
            "y", List.of(1)
        )));
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(42L, 101L));
        locationGraph.setGraph(graph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdWithGraphDetails(42L)).thenReturn(List.of(locationGraph));

        Map<Long, DashboardGraphProjection> first =
            service.resolveLocationMonthRangeProjections(42L, new DashboardGraphMonthRange(3));
        writeData(graph, List.of(Map.of(
            "type", "bar",
            "orientation", "v",
            "x", List.of("second"),
            "y", List.of(2)
        )));
        Map<Long, DashboardGraphProjection> second =
            service.resolveLocationMonthRangeProjections(42L, new DashboardGraphMonthRange(3));

        assertEquals(first.get(101L), second.get(101L));
        assertEquals(1L, cache.graphProjectionEntryCount());

        service.invalidateLocationCache(42L);

        Map<Long, DashboardGraphProjection> afterInvalidation =
            service.resolveLocationMonthRangeProjections(42L, new DashboardGraphMonthRange(3));
        assertEquals("second", ((Map<?, ?>) afterInvalidation.get(101L).data().getFirst()).get("x") instanceof List<?> x
            ? x.getFirst()
            : null);
    }

    @Test
    void scopedDerivedProjectionLoadsOnlyRequestedGraphsAndImportedDependencies() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            new LocationDashboardCache(),
            new LocationDashboardGraphMatcher(),
            new LocationDashboardHistoricalDataAssembler(),
            clock
        );
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");

        LocationDashboardImportStrategyConfig.GraphConfig importDefinition =
            new LocationDashboardImportStrategyConfig.GraphConfig(
                "water-quality",
                "Water Quality Conformance",
                "Newport Beach",
                LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                "newport-beach",
                List.of(),
                Map.of(),
                "scatter"
            );
        LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedDefinition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "total-samples",
                "Total Number of Samples",
                null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES,
                "pie"
            );
        LocationDashboardImportStrategy strategy = mock(LocationDashboardImportStrategy.class);
        when(strategy.graphDefinitions()).thenReturn(List.of(importDefinition));
        when(strategy.derivedGraphDefinitions()).thenReturn(List.of(derivedDefinition));
        when(strategy.spreadsheetIdentityPattern()).thenReturn(List.of());
        when(strategyRegistry.resolve("Hoag Hospital")).thenReturn(Optional.of(strategy));

        Graph importedGraph = new Graph();
        importedGraph.setId(101L);
        importedGraph.setName(importDefinition.name());
        importedGraph.setLayout(LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
            Map.of(), importDefinition, "Hoag Hospital"
        ));
        writeData(importedGraph, List.of(Map.of("type", "scatter", "x", List.of(), "y", List.of())));
        Graph derivedGraph = new Graph();
        derivedGraph.setId(202L);
        derivedGraph.setName(derivedDefinition.name());
        derivedGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of(), derivedDefinition, "Hoag Hospital"
        ));
        writeData(derivedGraph, List.of(Map.of(
            "type", "pie",
            "name", "Samples",
            "labels", List.of("Total Samples"),
            "values", List.of(99)
        )));
        LocationGraph importedAssignment = locationGraph(42L, importedGraph);
        LocationGraph derivedAssignment = locationGraph(42L, derivedGraph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(202L)))
        )).thenReturn(List.of(derivedAssignment));
        when(locationGraphRepository.findByLocationIdWithGraph(42L))
            .thenReturn(List.of(importedAssignment, derivedAssignment));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(101L, 202L)))
        )).thenReturn(List.of(importedAssignment, derivedAssignment));
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(42L))
            .thenReturn(List.of());
        when(samplePersistenceService.loadLocationSamples(42L)).thenReturn(List.of());

        Map<Long, DashboardGraphProjection> projections =
            service.resolveLocationMonthRangeProjections(
                42L,
                List.of(202L),
                new DashboardGraphMonthRange(2)
            );

        assertEquals(Set.of(202L), projections.keySet());
        assertEquals(List.of(0L), projections.get(202L).data().getFirst().get("values"));
        verify(locationGraphRepository).findByLocationIdWithGraph(42L);
        verify(locationGraphRepository).findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(101L, 202L)))
        );
    }

    @Test
    void concurrentScopedDerivedRequestsAssembleHistoricalDataOnce() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardHistoricalDataAssembler assembler = mock(LocationDashboardHistoricalDataAssembler.class);
        LocationDashboardTimeRangeService service = new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            new LocationDashboardCache(),
            new LocationDashboardGraphMatcher(),
            assembler,
            clock
        );
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");
        LocationDashboardImportStrategyConfig.GraphConfig importDefinition =
            new LocationDashboardImportStrategyConfig.GraphConfig(
                "water-quality", "Water Quality Conformance", "Newport Beach",
                LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                "newport-beach", List.of(), Map.of(), "scatter"
            );
        LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedDefinition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "total-samples", "Total Number of Samples", null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES, "pie"
            );
        LocationDashboardImportStrategy strategy = mock(LocationDashboardImportStrategy.class);
        when(strategy.graphDefinitions()).thenReturn(List.of(importDefinition));
        when(strategy.derivedGraphDefinitions()).thenReturn(List.of(derivedDefinition));
        when(strategy.spreadsheetIdentityPattern()).thenReturn(List.of());
        when(strategyRegistry.resolve("Hoag Hospital")).thenReturn(Optional.of(strategy));

        Graph importedGraph = new Graph();
        importedGraph.setId(101L);
        importedGraph.setName(importDefinition.name());
        importedGraph.setLayout(LocationDashboardGraphMetadataSupport.withImportMetadataAndDefaults(
            Map.of(), importDefinition, "Hoag Hospital"
        ));
        writeData(importedGraph, List.of(Map.of("type", "scatter", "x", List.of(), "y", List.of())));
        Graph derivedGraph = new Graph();
        derivedGraph.setId(202L);
        derivedGraph.setName(derivedDefinition.name());
        derivedGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of(), derivedDefinition, "Hoag Hospital"
        ));
        writeData(derivedGraph, List.of(Map.of(
            "type", "pie", "name", "Samples", "labels", List.of("Total Samples"), "values", List.of(99)
        )));
        LocationGraph importedAssignment = locationGraph(42L, importedGraph);
        LocationGraph derivedAssignment = locationGraph(42L, derivedGraph);
        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(202L)))
        )).thenReturn(List.of(derivedAssignment));
        when(locationGraphRepository.findByLocationIdWithGraph(42L))
            .thenReturn(List.of(importedAssignment, derivedAssignment));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(101L, 202L)))
        )).thenReturn(List.of(importedAssignment, derivedAssignment));
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(42L))
            .thenReturn(List.of());
        when(samplePersistenceService.loadLocationSamples(42L)).thenReturn(List.of());

        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        when(assembler.buildHistoricalDerivedData(
            any(), any(), any(), any(), any(), any(), any()
        )).thenAnswer(ignored -> {
            loaderStarted.countDown();
            releaseLoader.await();
            return new LocationDashboardDerivedGraphSupport.HistoricalDerivedData(Map.of(), List.of(), List.of());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Map<Long, DashboardGraphProjection>>> futures = java.util.stream.IntStream.range(0, 2)
                .mapToObj(ignored -> executor.submit(() -> {
                    start.await();
                    return service.resolveLocationMonthRangeProjections(
                        42L, List.of(202L), new DashboardGraphMonthRange(2)
                    );
                }))
                .toList();
            start.countDown();
            loaderStarted.await();
            releaseLoader.countDown();
            for (Future<Map<Long, DashboardGraphProjection>> future : futures) {
                assertEquals(Set.of(202L), future.get().keySet());
            }
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }

        verify(assembler, times(1)).buildHistoricalDerivedData(
            any(), any(), any(), any(), any(), any(), any()
        );
        verify(samplePersistenceService, times(1)).loadLocationSamples(42L);
        verify(serviceEventRepository, times(1))
            .findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(42L);
    }

    @Test
    void scopedTimeSeriesProjectionFiltersPersistedPointsToRequestedRange() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardTimeRangeService service = service(clock);
        Location location = new Location();
        location.setId(42L);
        location.setName("Unconfigured Location");
        Graph graph = timeSeriesGraph(
            101L,
            List.of(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 20)),
            List.of(1d, 2d, 3d)
        );
        LocationGraph assignment = locationGraph(42L, graph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(101L)))
        )).thenReturn(List.of(assignment));

        Map<Long, DashboardGraphProjection> projections = service.resolveLocationMonthRangeProjections(
            42L,
            List.of(101L),
            new DashboardGraphMonthRange(2)
        );

        Map<String, Object> trace = projections.get(101L).data().getFirst();
        assertEquals(List.of("2026-04-01", "2026-06-20"), trace.get("x"));
        assertEquals(
            List.of(2, 3),
            ((List<?>) trace.get("y")).stream().map(value -> ((Number) value).intValue()).toList()
        );
        assertEquals(Set.of(101L), projections.keySet());
    }

    @Test
    void scopedDerivedProjectionIsExplicitlyAbsentWhenLocationHasNoStrategy() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardTimeRangeService service = service(clock);
        Location location = new Location();
        location.setId(42L);
        location.setName("Unconfigured Location");
        LocationDashboardImportStrategyConfig.DerivedGraphConfig definition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "total-samples", "Total Number of Samples", null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES, "pie"
            );
        Graph derivedGraph = new Graph();
        derivedGraph.setId(202L);
        derivedGraph.setName(definition.name());
        derivedGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of(), definition, "Unconfigured Location"
        ));
        writeData(derivedGraph, List.of(Map.of(
            "type", "pie", "name", "Samples", "labels", List.of("Total Samples"), "values", List.of(99)
        )));

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(202L)))
        )).thenReturn(List.of(locationGraph(42L, derivedGraph)));
        when(strategyRegistry.resolve("Unconfigured Location")).thenReturn(Optional.empty());

        assertEquals(
            Map.of(),
            service.resolveLocationMonthRangeProjections(42L, List.of(202L), new DashboardGraphMonthRange(2))
        );
    }

    @Test
    void scopedDerivedProjectionIsExplicitlyAbsentWhenMetadataDoesNotMatchConfiguration() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC);
        LocationDashboardTimeRangeService service = service(clock);
        Location location = new Location();
        location.setId(42L);
        location.setName("Hoag Hospital");
        LocationDashboardImportStrategyConfig.DerivedGraphConfig persistedDefinition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "obsolete-total", "Obsolete total", null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES, "pie"
            );
        LocationDashboardImportStrategyConfig.DerivedGraphConfig configuredDefinition =
            new LocationDashboardImportStrategyConfig.DerivedGraphConfig(
                "configured-total", "Configured total", null,
                LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES, "pie"
            );
        LocationDashboardImportStrategy strategy = mock(LocationDashboardImportStrategy.class);
        when(strategy.graphDefinitions()).thenReturn(List.of());
        when(strategy.derivedGraphDefinitions()).thenReturn(List.of(configuredDefinition));
        when(strategyRegistry.resolve("Hoag Hospital")).thenReturn(Optional.of(strategy));

        Graph derivedGraph = new Graph();
        derivedGraph.setId(202L);
        derivedGraph.setName(persistedDefinition.name());
        derivedGraph.setLayout(LocationDashboardGraphMetadataSupport.withDerivedImportMetadata(
            Map.of(), persistedDefinition, "Hoag Hospital"
        ));
        writeData(derivedGraph, List.of(Map.of(
            "type", "pie", "name", "Samples", "labels", List.of("Total Samples"), "values", List.of(99)
        )));
        LocationGraph assignment = locationGraph(42L, derivedGraph);

        when(locationRepository.findById(42L)).thenReturn(Optional.of(location));
        when(locationGraphRepository.findByLocationIdAndGraphIdInWithGraphDetails(
            eq(42L), argThat(ids -> Set.copyOf(ids).equals(Set.of(202L)))
        )).thenReturn(List.of(assignment));
        when(locationGraphRepository.findByLocationIdWithGraph(42L)).thenReturn(List.of(assignment));

        assertEquals(
            Map.of(),
            service.resolveLocationMonthRangeProjections(42L, List.of(202L), new DashboardGraphMonthRange(2))
        );
    }

    private LocationDashboardTimeRangeService service(Clock clock) {
        return new LocationDashboardTimeRangeService(
            locationRepository,
            locationGraphRepository,
            serviceEventRepository,
            strategyRegistry,
            samplePersistenceService,
            new LocationDashboardCache(),
            new LocationDashboardGraphMatcher(),
            new LocationDashboardHistoricalDataAssembler(),
            clock
        );
    }

    private Graph timeSeriesGraph(Long graphId, List<LocalDate> dates, List<Double> values) {
        Graph graph = new Graph();
        graph.setId(graphId);
        graph.setName("Time series");
        graph.setLayout(Map.of());
        GraphTrace trace = new GraphTrace();
        trace.setGraph(graph);
        trace.setTraceKey("trace-0");
        trace.setTraceName("Samples");
        trace.setTraceType("scatter");
        trace.setDataMode("time_series");
        trace.setTraceOrder(0);
        trace.setTraceConfig(Map.of("mode", "lines"));
        List<GraphTimeSeriesPoint> points = java.util.stream.IntStream.range(0, dates.size())
            .mapToObj(index -> {
                GraphTimeSeriesPoint point = new GraphTimeSeriesPoint();
                point.setGraphTrace(trace);
                point.setObservedAt(dates.get(index).atStartOfDay().toInstant(ZoneOffset.UTC));
                point.setPointOrder(index);
                point.setYNumeric(BigDecimal.valueOf(values.get(index)));
                point.setPointMeta(Map.of("x", dates.get(index).toString()));
                return point;
            })
            .toList();
        trace.setTimeSeriesPoints(points);
        graph.setGraphTraces(List.of(trace));
        return graph;
    }

    private LocationGraph locationGraph(Long locationId, Graph graph) {
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(locationId, graph.getId()));
        locationGraph.setGraph(graph);
        return locationGraph;
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
