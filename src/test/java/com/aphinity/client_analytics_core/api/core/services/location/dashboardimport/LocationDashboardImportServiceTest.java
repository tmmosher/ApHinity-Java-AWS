package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.GraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.LocationGraphRepository;
import com.aphinity.client_analytics_core.api.core.repositories.dashboard.MeasurementBoundRepository;
import com.aphinity.client_analytics_core.api.core.repositories.location.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.servicecalendar.ServiceEventRepository;
import com.aphinity.client_analytics_core.api.core.response.dashboard.GraphResponse;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationDashboardImportServiceTest {
    @Mock
    private LocationDashboardSpreadsheetParser spreadsheetParser;

    @Mock
    private LocationDashboardImportStrategyRegistry strategyRegistry;

    @Mock
    private MeasurementBoundRepository measurementBoundRepository;

    @Mock
    private LocationGraphRepository locationGraphRepository;

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private ServiceEventRepository serviceEventRepository;

    @Mock
    private LocationRepository locationRepository;

    @Test
    void importLocationDashboardUpdatesGraphsAndCreatesCorrectiveActions() {
        LocationDashboardImportService importService = buildImportService();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1, 2, 3}
        );
        Location location = new Location();
        location.setId(9L);
        location.setName("Newport Beach");

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbook("Newport Beach", "Drain Tank, install new DI bottles", "F5");
        when(spreadsheetParser.parse(file)).thenReturn(workbook);

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = graph(18L, "Water Quality Compliance");
        Graph systemTypeGraph = graph(19L, "System Type Compliance");
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(List.of(
            locationGraph(9L, waterQualityGraph),
            locationGraph(9L, systemTypeGraph)
        ));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(9L), anyList()))
            .thenReturn(List.of(waterQualityGraph, systemTypeGraph));
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndEventDateIn(9L, List.of(LocalDate.parse("2025-08-01"))))
            .thenReturn(List.of());
        when(graphRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceEventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(2, responses.size());
        assertEquals("Water Quality Compliance", responses.getFirst().name());
        assertEquals(List.of(100L), responses.getFirst().data().getFirst().get("y"));
        assertEquals("System Type Compliance", responses.get(1).name());
        assertEquals(List.of(50L), responses.get(1).data().getFirst().get("y"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) responses.getFirst().layout().get("meta");
        assertTrue(meta.containsKey("aphinityImport"));

        verify(serviceEventRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat((Iterable<ServiceEvent> events) -> {
            List<ServiceEvent> eventList = new java.util.ArrayList<>();
            events.forEach(eventList::add);
            if (eventList.size() != 1) {
                return false;
            }
            ServiceEvent event = eventList.getFirst();
            return event.isCorrectiveAction()
                && event.getTitle().startsWith("CA: HPC 2025-08-01")
                && event.getDescription().contains("CA: Drain Tank, install new DI bottles");
        }));
        verify(locationRepository).touchUpdatedAt(eq(9L), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void importLocationDashboardRejectsWorkbookTitleMismatchBeforeLoadingMeasurements() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1, 2, 3}
        );
        Location location = new Location();
        location.setId(9L);
        location.setName("Newport Beach");

        when(spreadsheetParser.parse(file)).thenReturn(workbook("Wrong Beach", "Drain Tank, install new DI bottles", "F5"));
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(buildStrategy()));

        ApiClientException error = assertThrows(
            ApiClientException.class,
            () -> importService.importLocationDashboard(location, file)
        );

        assertEquals("location_dashboard_location_title_mismatch", error.getCode());
        verifyNoInteractions(measurementBoundRepository, locationGraphRepository, graphRepository, serviceEventRepository, locationRepository);
    }

    @Test
    void importLocationDashboardUpdatesExistingCorrectiveActionAcrossShiftedCellReference() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1, 2, 3}
        );
        Location location = new Location();
        location.setId(9L);
        location.setName("Newport Beach");

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy();
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(spreadsheetParser.parse(file)).thenReturn(workbook("Newport Beach", "Replace DI bottles", "F6"));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = graph(18L, "Water Quality Compliance");
        Graph systemTypeGraph = graph(19L, "System Type Compliance");
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(List.of(
            locationGraph(9L, waterQualityGraph),
            locationGraph(9L, systemTypeGraph)
        ));
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(9L), anyList()))
            .thenReturn(List.of(waterQualityGraph, systemTypeGraph));

        ServiceEvent existingEvent = new ServiceEvent();
        existingEvent.setId(501L);
        existingEvent.setLocation(location);
        existingEvent.setCorrectiveAction(true);
        existingEvent.setEventDate(LocalDate.parse("2025-08-01"));
        existingEvent.setTitle(strategy.computeImport(
            workbook("Newport Beach", "Initial DI bottle change", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst().title());
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndEventDateIn(9L, List.of(LocalDate.parse("2025-08-01"))))
            .thenReturn(List.of(existingEvent));

        when(graphRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceEventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        importService.importLocationDashboard(location, file);

        verify(serviceEventRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat((Iterable<ServiceEvent> events) -> {
            List<ServiceEvent> eventList = new java.util.ArrayList<>();
            events.forEach(eventList::add);
            if (eventList.size() != 1) {
                return false;
            }
            ServiceEvent event = eventList.getFirst();
            return event == existingEvent
                && event.getDescription().contains("CA: Replace DI bottles")
                && event.getTitle().startsWith("CA: HPC 2025-08-01");
        }));
    }

    private LocationDashboardImportService buildImportService() {
        return new LocationDashboardImportService(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            graphRepository,
            serviceEventRepository,
            locationRepository,
            new LocationDashboardMutationLockService(),
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private ConfiguredLocationDashboardImportStrategy buildStrategy() {
        return new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Newport Beach",
                List.of(new LocationDashboardImportStrategyConfig.SublocationConfig(
                    "newport-beach",
                    "Newport Beach",
                    List.of("Newport Beach"),
                    List.of(),
                    true
                )),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                    "cooling-towers",
                    "Cooling Towers",
                    LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                    List.of("Cooling Towers")
                )),
                List.of(
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "water-quality",
                        "Water Quality Compliance",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "newport-beach",
                        List.of("HPC", "Endotoxin"),
                        Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "system-type",
                        "System Type Compliance",
                        LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                        "newport-beach",
                        List.of("Cooling Towers"),
                        Map.of("Cooling Towers", "#d62728"),
                        "scatter"
                    )
                )
            )
        );
    }

    private List<MeasurementBound> measurementBounds() {
        return List.of(
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10")),
            measurementBound(2L, "Endotoxin", null, null, null, null, null, null, null, new BigDecimal("1"))
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook(
        String locationTitle,
        String correctiveActionDescription,
        String firstCellReference
    ) {
        return new LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook(
            locationTitle,
            List.of(
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    5,
                    "Newport Beach",
                    "Hospital",
                    "Cooling Towers",
                    "Recirc Line",
                    "CTI/514P",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "10",
                            new BigDecimal("10"),
                            "Test 1;350;CA;" + correctiveActionDescription,
                            firstCellReference
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Endotoxin",
                            LocalDate.parse("2025-08-01"),
                            "2",
                            new BigDecimal("2"),
                            null,
                            "I5"
                        )
                    )
                )
            )
        );
    }

    private Graph graph(Long id, String name) {
        Graph graph = new Graph();
        graph.setId(id);
        graph.setName(name);
        graph.setGraphType("scatter");
        graph.setLayout(Map.of());
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        graph.setData(List.of());
        return graph;
    }

    private LocationGraph locationGraph(Long locationId, Graph graph) {
        LocationGraph locationGraph = new LocationGraph();
        locationGraph.setId(new LocationGraphId(locationId, graph.getId()));
        locationGraph.setGraph(graph);
        return locationGraph;
    }

    private MeasurementBound measurementBound(
        Long id,
        String name,
        BigDecimal criticalMin,
        BigDecimal criticalMax,
        BigDecimal utilityMin,
        BigDecimal utilityMax,
        BigDecimal potableMin,
        BigDecimal potableMax,
        BigDecimal towersMin,
        BigDecimal towersMax
    ) {
        MeasurementBound measurementBound = new MeasurementBound();
        measurementBound.setId(id);
        measurementBound.setMeasurementName(name);
        measurementBound.setCriticalRangeMin(criticalMin);
        measurementBound.setCriticalRangeMax(criticalMax);
        measurementBound.setUtilityRangeMin(utilityMin);
        measurementBound.setUtilityRangeMax(utilityMax);
        measurementBound.setPotableRangeMin(potableMin);
        measurementBound.setPotableRangeMax(potableMax);
        measurementBound.setTowersRangeMin(towersMin);
        measurementBound.setTowersRangeMax(towersMax);
        return measurementBound;
    }
}
