package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.LocationGraphId;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    void importLocationDashboardUpdatesConfiguredAndDerivedGraphs() {
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

        Graph waterQualityGraph = scatterGraph(18L, "Water Quality Compliance");
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance");
        Graph totalSamplesGraph = pieGraph(20L, "Total Number of Samples");
        Graph totalNonConformancesGraph = pieGraph(21L, "Total Non-Conformances");
        Graph activePercentGraph = pieGraph(22L, "Active Non-Conformance Percent");
        Graph nonConformancesByFacilityGraph = barGraph(23L, "Non-Conformances", "By Facility");
        Graph nonConformanceStatusByFacilityGraph = barGraph(24L, "Non-Conformance Status", "By Facility");
        Graph turnaroundTimeGraph = barGraph(25L, "Non-Conformance Status", "Turnaround Time");
        List<Graph> graphs = List.of(
            locationGraph(9L, waterQualityGraph),
            locationGraph(9L, systemTypeGraph),
            locationGraph(9L, totalSamplesGraph),
            locationGraph(9L, totalNonConformancesGraph),
            locationGraph(9L, activePercentGraph),
            locationGraph(9L, nonConformancesByFacilityGraph),
            locationGraph(9L, nonConformanceStatusByFacilityGraph),
            locationGraph(9L, turnaroundTimeGraph)
        ).stream().map(LocationGraph::getGraph).toList();
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(9L), anyList()))
            .thenReturn(graphs);
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndTitleIn(eq(9L), anyList()))
            .thenReturn(List.of());
        when(graphRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceEventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(8, responses.size());
        assertEquals(List.of(0L), findResponseByName(responses, "Water Quality Compliance").data().getFirst().get("y"));
        assertEquals(List.of(0L), findResponseByName(responses, "System Type Compliance").data().getFirst().get("y"));
        assertEquals(List.of(2L), findResponseByName(responses, "Total Number of Samples").data().getFirst().get("values"));
        assertEquals(List.of(1L), findResponseByName(responses, "Total Non-Conformances").data().getFirst().get("values"));
        assertEquals(List.of(100L, 0L), findResponseByName(responses, "Active Non-Conformance Percent").data().getFirst().get("values"));
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of("Newport Beach"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("y"));
        assertEquals("Active", findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst().get("name"));
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of(), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("x"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) findResponseByName(responses, "Water Quality Compliance").layout().get("meta");
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
                && event.getDescription().contains("CA: Drain Tank, install new DI bottles")
                && event.getStatus() == ServiceEventStatus.OVERDUE;
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
    void importLocationDashboardPreservesExistingCorrectiveActionResolutionStateOnReimport() {
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

        Graph waterQualityGraph = scatterGraph(18L, "Water Quality Compliance");
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance");
        Graph resolutionPercentGraph = indicatorGraph(20L, "Resolution Percent");
        Graph turnaroundTimeGraph = barGraph(21L, "Non-Conformance Status", "Turnaround Time");
        Graph nonConformanceStatusByFacilityGraph = barGraph(22L, "Non-Conformance Status", "By Facility");
        List<Graph> graphs = List.of(
            locationGraph(9L, waterQualityGraph),
            locationGraph(9L, systemTypeGraph),
            locationGraph(9L, resolutionPercentGraph),
            locationGraph(9L, turnaroundTimeGraph),
            locationGraph(9L, nonConformanceStatusByFacilityGraph)
        ).stream().map(LocationGraph::getGraph).toList();
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());
        when(graphRepository.findByLocationIdAndGraphIdInForUpdate(eq(9L), anyList()))
            .thenReturn(graphs);

        ServiceEvent existingEvent = new ServiceEvent();
        existingEvent.setId(501L);
        existingEvent.setLocation(location);
        existingEvent.setCorrectiveAction(true);
        existingEvent.setEventDate(LocalDate.parse("2025-08-01"));
        existingEvent.setEventTime(LocalTime.MIDNIGHT);
        existingEvent.setEndEventDate(LocalDate.parse("2025-08-03"));
        existingEvent.setEndEventTime(LocalTime.NOON);
        existingEvent.setStatus(ServiceEventStatus.COMPLETED);
        existingEvent.setTitle(strategy.computeImport(
            workbook("Newport Beach", "Initial DI bottle change", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst().title());
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueAndTitleIn(eq(9L), anyList()))
            .thenReturn(List.of(existingEvent));

        when(graphRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceEventRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(5, responses.size());
        assertEquals(100L, ((Number) findResponseByName(responses, "Resolution Percent").data().getFirst().get("value")).longValue());
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("x"));
        assertEquals(List.of("2 days"), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("y"));
        assertEquals(List.of(0L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().get(1).get("x"));

        verify(serviceEventRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat((Iterable<ServiceEvent> events) -> {
            List<ServiceEvent> eventList = new java.util.ArrayList<>();
            events.forEach(eventList::add);
            if (eventList.size() != 1) {
                return false;
            }
            ServiceEvent event = eventList.getFirst();
            return event == existingEvent
                && event.getDescription().contains("CA: Replace DI bottles")
                && event.getTitle().startsWith("CA: HPC 2025-08-01")
                && event.getStatus() == ServiceEventStatus.COMPLETED
                && LocalDate.parse("2025-08-03").equals(event.getEndEventDate())
                && LocalTime.NOON.equals(event.getEndEventTime());
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

    private Graph scatterGraph(Long id, String name) {
        return graph(id, name, "scatter", Map.of(), List.of());
    }

    private Graph pieGraph(Long id, String name) {
        return graph(id, name, "pie", Map.of(
            "annotations", List.of(Map.of(
                "x", 0.5,
                "y", 0.5,
                "text", "<b>0</b>",
                "xref", "paper",
                "yref", "paper",
                "showarrow", false
            ))
        ), List.of(Map.of(
            "type", "pie",
            "name", "Trace 1",
            "hole", 0.72,
            "labels", List.of("fill"),
            "values", List.of(0)
        )));
    }

    private Graph indicatorGraph(Long id, String name) {
        return graph(id, name, "indicator", Map.of(), List.of(Map.of(
            "type", "indicator",
            "name", "Trace 1",
            "mode", "gauge+number",
            "value", 0,
            "number", Map.of("suffix", "%"),
            "gauge", Map.of("axis", Map.of("range", List.of(0, 100)))
        )));
    }

    private Graph barGraph(Long id, String name, String titleText) {
        return graph(id, name, "bar", Map.of(
            "title", Map.of("text", titleText)
        ), List.of(Map.of(
            "type", "bar",
            "name", "Trace 1",
            "x", List.of(),
            "y", List.of(),
            "orientation", "h"
        )));
    }

    private Graph graph(Long id, String name, String graphType, Map<String, Object> layout, List<Map<String, Object>> data) {
        Graph graph = new Graph();
        graph.setId(id);
        graph.setName(name);
        graph.setGraphType(graphType);
        graph.setLayout(new LinkedHashMap<>(layout));
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        graph.setData(data);
        return graph;
    }

    private GraphResponse findResponseByName(List<GraphResponse> responses, String graphName) {
        return responses.stream()
            .filter(response -> graphName.equals(response.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Graph response was not returned for " + graphName));
    }

    @SuppressWarnings("unchecked")
    private GraphResponse findResponseByNameAndTitle(List<GraphResponse> responses, String graphName, String titleText) {
        return responses.stream()
            .filter(response -> graphName.equals(response.name()))
            .filter(response -> {
                Object title = response.layout().get("title");
                if (!(title instanceof Map<?, ?> titleMap)) {
                    return false;
                }
                return titleText.equals(titleMap.get("text"));
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("Graph response was not returned for " + graphName + " / " + titleText));
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
