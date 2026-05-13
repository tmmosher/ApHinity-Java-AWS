package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTimeSeriesPoint;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.GraphTrace;
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
import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    void importLocationDashboardBuildsPreviewImportedTimeSeriesAndDerivedGraphsFromHistoricalData() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbook("Newport Beach", "Drain Tank, install new DI bottles", "F5");
        when(spreadsheetParser.parse(file)).thenReturn(workbook);

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(allDerivedGraphDefinitions());
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = graph(
            18L,
            "Water Quality Compliance",
            "scatter",
            Map.of("title", Map.of("text", "Newport Beach")),
            List.of(
                scatterTrace(
                    "HPC",
                    List.of("2025-07-01", "2025-08-01"),
                    List.of(100.0d, 100.0d),
                    List.of(
                        Map.of("sampleCount", 3, "compliantCount", 3, "nonConformingCount", 0),
                        Map.of("sampleCount", 9, "compliantCount", 9, "nonConformingCount", 0)
                    )
                ),
                scatterTrace("Endotoxin", List.of(), List.of(), List.of())
            )
        );
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        Graph totalSamplesGraph = pieGraph(20L, "Total Number of Samples", "#0f766e");
        Graph totalNonConformancesGraph = pieGraph(21L, "Total Non-Conformances");
        Graph resolutionPercentGraph = indicatorGraph(22L, "Percent Resolved", "#9333ea");
        Graph percentConformanceGraph = indicatorGraph(23L, "Percent Conformance");
        Graph byWaterQualityGraph = barGraph(24L, "Non-Conformances", "By Water Quality Category");
        Graph bySystemTypeGraph = barGraph(25L, "Non-Conformances", "By Water System Type");
        Graph byFacilityGraph = barGraph(26L, "Non-Conformances", "By Facility");
        Graph statusByFacilityGraph = barGraph(27L, "Non-Conformance Status", "By Facility");
        Graph turnaroundTimeGraph = barGraph(28L, "Non-Conformance Status", "Turnaround Time");

        List<Graph> graphs = List.of(
            waterQualityGraph,
            systemTypeGraph,
            totalSamplesGraph,
            totalNonConformancesGraph,
            resolutionPercentGraph,
            percentConformanceGraph,
            byWaterQualityGraph,
            bySystemTypeGraph,
            byFacilityGraph,
            statusByFacilityGraph,
            turnaroundTimeGraph
        );
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());

        LocationDashboardImportStrategy.CorrectiveActionDraft importedDraft = strategy.computeImport(workbook, measurementBounds())
            .correctiveActions()
            .getFirst();
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(
                correctiveActionEvent(
                    location,
                    501L,
                    "CA: Historic HPC",
                    correctiveActionDescription("HPC", LocalDate.parse("2025-07-01"), "Newport Beach", "Cooling Towers", "Historic cleanup"),
                    LocalDate.parse("2025-07-01"),
                    LocalDate.parse("2025-07-03"),
                    LocalTime.NOON,
                    ServiceEventStatus.COMPLETED
                ),
                correctiveActionEvent(
                    location,
                    502L,
                    importedDraft.title(),
                    importedDraft.description(),
                    LocalDate.parse("2025-08-01"),
                    LocalDate.parse("2025-08-01"),
                    LocalTime.of(23, 59, 59),
                    ServiceEventStatus.OVERDUE
                )
            ));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(11, responses.size());
        assertEquals(List.of("2025-07-01", "2025-08-01"), findResponseByName(responses, "Water Quality Compliance").data().getFirst().get("x"));
        assertNumericValues(findResponseByName(responses, "Water Quality Compliance").data().getFirst().get("y"), 100.0d, 0.0d);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hpcCustomData = (List<Map<String, Object>>) findResponseByName(responses, "Water Quality Compliance")
            .data()
            .getFirst()
            .get("customdata");
        assertEquals(2L, ((Number) hpcCustomData.get(1).get("sampleCount")).longValue());

        assertEquals(List.of(7L), findResponseByName(responses, "Total Number of Samples").data().getFirst().get("values"));
        @SuppressWarnings("unchecked")
        Map<String, Object> totalSamplesMarker = (Map<String, Object>) findResponseByName(
            responses,
            "Total Number of Samples"
        ).data().getFirst().get("marker");
        assertEquals(
            "#0f766e",
            totalSamplesMarker.get("color")
        );
        assertEquals(List.of(4L), findResponseByName(responses, "Total Non-Conformances").data().getFirst().get("values"));
        assertEquals(25L, ((Number) findResponseByName(responses, "Percent Resolved").data().getFirst().get("value")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> resolutionPercentGauge = (Map<String, Object>) findResponseByName(
            responses,
            "Percent Resolved"
        ).data().getFirst().get("gauge");
        @SuppressWarnings("unchecked")
        Map<String, Object> resolutionPercentBar = (Map<String, Object>) resolutionPercentGauge.get("bar");
        assertEquals("#9333ea", resolutionPercentBar.get("color"));
        assertEquals(57L, ((Number) findResponseByName(responses, "Percent Conformance").data().getFirst().get("value")).longValue());
        assertEquals(List.of(3L, 1L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water Quality Category").data().getFirst().get("x"));
        assertEquals(List.of("HPC", "Endotoxin"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water Quality Category").data().getFirst().get("y"));
        assertEquals(List.of(4L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water System Type").data().getFirst().get("x"));
        assertEquals(List.of("Cooling Towers"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water System Type").data().getFirst().get("y"));
        assertEquals(List.of(4L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of("Newport Beach"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("y"));
        assertEquals(List.of(3L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().get(1).get("x"));
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("x"));
        assertEquals(List.of("< 3 days"), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("y"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) findResponseByName(responses, "Percent Conformance").layout().get("meta");
        assertTrue(meta.containsKey("aphinityImport"));
        verify(serviceEventRepository).findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L);
        verifyNoInteractions(graphRepository, locationRepository);
        verifyNoMoreInteractions(serviceEventRepository);
    }

    @Test
    void importLocationDashboardReadsHistoricalSampleCountsFromLegacyTraceLevelCustomData() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbook("Newport Beach", "Drain Tank, install new DI bottles", "F5");
        when(spreadsheetParser.parse(file)).thenReturn(workbook);

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(allDerivedGraphDefinitions());
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = legacyWaterQualityGraph(
            18L,
            "Water Quality Compliance",
            "Newport Beach",
            "HPC",
            List.of("2025-07-01", "2025-08-01"),
            List.of(100.0d, 100.0d),
            List.of(
                Map.of("sampleCount", 3, "compliantCount", 3, "nonConformingCount", 0),
                Map.of("sampleCount", 9, "compliantCount", 9, "nonConformingCount", 0)
            )
        );
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        Graph totalSamplesGraph = pieGraph(20L, "Total Number of Samples", "#0f766e");
        Graph totalNonConformancesGraph = pieGraph(21L, "Total Non-Conformances");
        Graph resolutionPercentGraph = indicatorGraph(22L, "Percent Resolved", "#9333ea");
        Graph percentConformanceGraph = indicatorGraph(23L, "Percent Conformance");
        Graph byWaterQualityGraph = barGraph(24L, "Non-Conformances", "By Water Quality Category");
        Graph bySystemTypeGraph = barGraph(25L, "Non-Conformances", "By Water System Type");
        Graph byFacilityGraph = barGraph(26L, "Non-Conformances", "By Facility");
        Graph statusByFacilityGraph = barGraph(27L, "Non-Conformance Status", "By Facility");
        Graph turnaroundTimeGraph = barGraph(28L, "Non-Conformance Status", "Turnaround Time");

        List<Graph> graphs = List.of(
            waterQualityGraph,
            systemTypeGraph,
            totalSamplesGraph,
            totalNonConformancesGraph,
            resolutionPercentGraph,
            percentConformanceGraph,
            byWaterQualityGraph,
            bySystemTypeGraph,
            byFacilityGraph,
            statusByFacilityGraph,
            turnaroundTimeGraph
        );
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());

        LocationDashboardImportStrategy.CorrectiveActionDraft importedDraft = strategy.computeImport(workbook, measurementBounds())
            .correctiveActions()
            .getFirst();
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(
                correctiveActionEvent(
                    location,
                    501L,
                    "CA: Historic HPC",
                    correctiveActionDescription("HPC", LocalDate.parse("2025-07-01"), "Newport Beach", "Cooling Towers", "Historic cleanup"),
                    LocalDate.parse("2025-07-01"),
                    LocalDate.parse("2025-07-03"),
                    LocalTime.NOON,
                    ServiceEventStatus.COMPLETED
                ),
                correctiveActionEvent(
                    location,
                    502L,
                    importedDraft.title(),
                    importedDraft.description(),
                    LocalDate.parse("2025-08-01"),
                    LocalDate.parse("2025-08-01"),
                    LocalTime.of(23, 59, 59),
                    ServiceEventStatus.OVERDUE
                )
            ));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(List.of("2025-07-01", "2025-08-01"), findResponseByName(responses, "Water Quality Compliance").data().getFirst().get("x"));
        assertNumericValues(findResponseByName(responses, "Water Quality Compliance").data().getFirst().get("y"), 100.0d, 0.0d);
        assertEquals(List.of(7L), findResponseByName(responses, "Total Number of Samples").data().getFirst().get("values"));
    }

    @Test
    void importLocationDashboardCountsOutOfSpecStructuredCommentSamplesAsNonConformances() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        String structuredComment = """
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL"
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-08-15",
                  "resultReceivedOn": "2025-08-20",
                  "resultRaw": "15 CFU.mL",
                  "resultValue": 15,
                  "resultUnit": "CFU.mL",
                  "correctiveActions": [
                    {
                      "text": "Disinfect and retest"
                    }
                  ]
                }
              ]
            }
            """;

        when(spreadsheetParser.parse(file)).thenReturn(workbookWithRawComment("Newport Beach", structuredComment, "F5"));

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(allDerivedGraphDefinitions());
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = scatterGraph(18L, "Water Quality Compliance", "Newport Beach");
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        Graph totalSamplesGraph = pieGraph(20L, "Total Number of Samples", "#0f766e");
        Graph totalNonConformancesGraph = pieGraph(21L, "Total Non-Conformances");
        Graph resolutionPercentGraph = indicatorGraph(22L, "Percent Resolved", "#9333ea");
        Graph percentConformanceGraph = indicatorGraph(23L, "Percent Conformance");
        Graph byWaterQualityGraph = barGraph(24L, "Non-Conformances", "By Water Quality Category");
        Graph bySystemTypeGraph = barGraph(25L, "Non-Conformances", "By Water System Type");
        Graph byFacilityGraph = barGraph(26L, "Non-Conformances", "By Facility");
        Graph statusByFacilityGraph = barGraph(27L, "Non-Conformance Status", "By Facility");
        Graph turnaroundTimeGraph = barGraph(28L, "Non-Conformance Status", "Turnaround Time");

        List<Graph> graphs = List.of(
            waterQualityGraph,
            systemTypeGraph,
            totalSamplesGraph,
            totalNonConformancesGraph,
            resolutionPercentGraph,
            percentConformanceGraph,
            byWaterQualityGraph,
            bySystemTypeGraph,
            byFacilityGraph,
            statusByFacilityGraph,
            turnaroundTimeGraph
        );
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of());

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(List.of(4L), findResponseByName(responses, "Total Non-Conformances").data().getFirst().get("values"));
        assertEquals(List.of(3L, 1L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water Quality Category").data().getFirst().get("x"));
        assertEquals(List.of("HPC", "Endotoxin"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water Quality Category").data().getFirst().get("y"));
        assertEquals(List.of(4L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water System Type").data().getFirst().get("x"));
        assertEquals(List.of("Cooling Towers"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Water System Type").data().getFirst().get("y"));
        assertEquals(List.of(4L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of("Newport Beach"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("y"));
    }

    private void assertNumericValues(Object rawValues, double... expectedValues) {
        @SuppressWarnings("unchecked")
        List<Number> values = (List<Number>) rawValues;
        assertEquals(expectedValues.length, values.size());
        for (int index = 0; index < expectedValues.length; index += 1) {
            assertEquals(expectedValues[index], values.get(index).doubleValue());
        }
    }

    @Test
    void importLocationDashboardMatchesGraphsByGraphNameAndLayoutTitle() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbook("Newport Beach", "Drain Tank, install new DI bottles", "F5");
        when(spreadsheetParser.parse(file)).thenReturn(workbook);

        ConfiguredLocationDashboardImportStrategy strategy = new ConfiguredLocationDashboardImportStrategy(
            new LocationDashboardImportStrategyConfig(
                "Newport Beach",
                List.of(
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "newport-beach",
                        "Newport Beach",
                        List.of("Newport Beach"),
                        List.of(),
                        true
                    ),
                    new LocationDashboardImportStrategyConfig.SublocationConfig(
                        "irvine",
                        "Irvine",
                        List.of("Irvine"),
                        List.of(),
                        true
                    )
                ),
                List.of(new LocationDashboardImportStrategyConfig.SystemTypeConfig(
                    "cooling-towers",
                    "Cooling Towers",
                    LocationDashboardImportStrategyConfig.RangeProfile.TOWERS,
                    List.of("Cooling Towers")
                )),
                List.of(
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "newport-beach-water-quality-compliance",
                        "Water Quality Compliance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "newport-beach",
                        List.of("HPC", "Endotoxin"),
                        Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "irvine-water-quality-compliance",
                        "Water Quality Compliance",
                        "Irvine",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "irvine",
                        List.of("HPC", "Endotoxin"),
                        Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                        "scatter"
                    )
                ),
                List.of(),
                List.of()
            )
        );

        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph newportQualityGraph = scatterGraph(18L, "Water Quality Compliance", "Newport Beach");
        Graph irvineQualityGraph = scatterGraph(19L, "Water Quality Compliance", "Irvine");
        List<Graph> graphs = List.of(newportQualityGraph, irvineQualityGraph);
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of());

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(2, responses.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> importMeta = (Map<String, Object>) ((Map<String, Object>) responses.getFirst().layout().get("meta")).get("aphinityImport");
        assertEquals("Water Quality Compliance", importMeta.get("graphName"));
        assertEquals("Newport Beach", importMeta.get("graphTitle"));
        assertEquals(
            List.of("Newport Beach", "Irvine"),
            responses.stream()
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> title = (Map<String, Object>) response.layout().get("title");
                    return (String) title.get("text");
                })
                .toList()
        );
    }

    @Test
    void importLocationDashboardReplacesNamedEmptyWaterQualityPlaceholderTraceData() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook =
            workbook("Newport Beach", "Drain Tank, install new DI bottles", "F5");
        when(spreadsheetParser.parse(file)).thenReturn(workbook);

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(List.of());
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = graph(
            18L,
            "Water Quality Compliance",
            "scatter",
            Map.of("title", Map.of("text", "Newport Beach")),
            List.of(
                Map.of(
                    "type", "scatter",
                    "name", "HPC",
                    "x", List.of(),
                    "y", List.of(),
                    "customdata", List.of(),
                    "mode", "lines+markers",
                    "line", Map.of("color", "#123456", "width", 4),
                    "marker", Map.of("size", 9)
                ),
                Map.of(
                    "type", "scatter",
                    "name", "Endotoxin",
                    "x", List.of(),
                    "y", List.of(),
                    "customdata", List.of(),
                    "mode", "lines+markers"
                )
            )
        );
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(List.of(waterQualityGraph, systemTypeGraph).stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of());

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        GraphResponse waterQualityResponse = findResponseByName(responses, "Water Quality Compliance");
        assertEquals(2, waterQualityResponse.data().size());
        assertEquals("HPC", waterQualityResponse.data().getFirst().get("name"));
        assertEquals(List.of("2025-08-01"), waterQualityResponse.data().getFirst().get("x"));
        assertNumericValues(waterQualityResponse.data().getFirst().get("y"), 0.0d);
        assertEquals(Map.of("color", "#123456", "width", 4L), waterQualityResponse.data().getFirst().get("line"));
        assertEquals(Map.of("size", 9L), waterQualityResponse.data().getFirst().get("marker"));
    }

    @Test
    void importLocationDashboardRejectsWorkbookTitleMismatchBeforeLoadingMeasurements() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        when(spreadsheetParser.parse(file)).thenReturn(workbook("Wrong Beach", "Drain Tank, install new DI bottles", "F5"));
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(buildStrategy(allDerivedGraphDefinitions())));

        ApiClientException error = assertThrows(
            ApiClientException.class,
            () -> importService.importLocationDashboard(location, file)
        );

        assertEquals("location_dashboard_location_title_mismatch", error.getCode());
        verifyNoInteractions(measurementBoundRepository, locationGraphRepository, graphRepository, serviceEventRepository, locationRepository);
    }

    @Test
    void importLocationDashboardPreservesExistingCorrectiveActionResolutionStateInPreview() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(List.of(
            derivedGraphDefinition("resolution-percent", "Percent Resolved", null, LocationDashboardImportStrategyConfig.DerivedGraphType.PERCENT_RESOLVED, "indicator"),
            derivedGraphDefinition("non-conformance-status-by-turnaround-time", "Non-Conformance Status", "Turnaround Time", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME, "bar"),
            derivedGraphDefinition("non-conformance-status-by-facility", "Non-Conformance Status", "By Facility", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_STATUS_BY_FACILITY, "bar")
        ));
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(spreadsheetParser.parse(file)).thenReturn(workbook("Newport Beach", "Replace DI bottles", "F6"));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = scatterGraph(18L, "Water Quality Compliance", "Newport Beach");
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        Graph resolutionPercentGraph = indicatorGraph(20L, "Percent Resolved");
        Graph turnaroundTimeGraph = barGraph(21L, "Non-Conformance Status", "Turnaround Time");
        Graph statusByFacilityGraph = verticalStatusByFacilityGraph(
            22L,
            "Non-Conformance Status",
            "By Facility",
            "#b91c1c",
            "#15803d"
        );
        List<Graph> graphs = List.of(
            waterQualityGraph,
            systemTypeGraph,
            resolutionPercentGraph,
            turnaroundTimeGraph,
            statusByFacilityGraph
        );
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());

        LocationDashboardImportStrategy.CorrectiveActionDraft originalDraft = strategy.computeImport(
            workbook("Newport Beach", "Initial DI bottle change", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst();

        ServiceEvent existingEvent = correctiveActionEvent(
            location,
            501L,
            originalDraft.title(),
            originalDraft.description(),
            LocalDate.parse("2025-08-01"),
            LocalDate.parse("2025-08-03"),
            LocalTime.NOON,
            ServiceEventStatus.COMPLETED
        );
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(existingEvent));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(5, responses.size());
        assertEquals(33L, ((Number) findResponseByName(responses, "Percent Resolved").data().getFirst().get("value")).longValue());
        assertEquals(List.of(1L), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("x"));
        assertEquals(List.of("< 3 days"), findResponseByNameAndTitle(responses, "Non-Conformance Status", "Turnaround Time").data().getFirst().get("y"));

        Map<String, Object> activeByFacility = findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst();
        Map<String, Object> resolvedByFacility = findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().get(1);
        assertEquals("v", activeByFacility.get("orientation"));
        assertEquals("v", resolvedByFacility.get("orientation"));
        assertEquals(List.of("Newport Beach"), activeByFacility.get("x"));
        assertEquals(List.of("Newport Beach"), resolvedByFacility.get("x"));
        assertEquals(List.of(2L), activeByFacility.get("y"));
        assertEquals(List.of(1L), resolvedByFacility.get("y"));
        assertEquals(Map.of("color", "#b91c1c"), activeByFacility.get("marker"));
        assertEquals(Map.of("color", "#15803d"), resolvedByFacility.get("marker"));

        verify(serviceEventRepository).findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L);
        verifyNoInteractions(graphRepository, locationRepository);
        verifyNoMoreInteractions(serviceEventRepository);
    }

    @Test
    void importLocationDashboardDropsMetadataLessCorrectiveActionsFromFacilityBreakdowns() {
        LocationDashboardImportService importService = buildImportService();
        MockMultipartFile file = dashboardFile();
        Location location = location(9L, "Newport Beach");

        ConfiguredLocationDashboardImportStrategy strategy = buildStrategy(List.of(
            derivedGraphDefinition("total-non-conformances", "Total Non-Conformances", null, LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_NON_CONFORMANCES, "pie"),
            derivedGraphDefinition("non-conformances-by-facility", "Non-Conformances", "By Facility", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCES_BY_FACILITY, "bar"),
            derivedGraphDefinition("non-conformance-status-by-turnaround-time", "Non-Conformance Status", "Turnaround Time", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME, "bar"),
            derivedGraphDefinition("non-conformance-status-by-facility", "Non-Conformance Status", "By Facility", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_STATUS_BY_FACILITY, "bar")
        ));
        when(strategyRegistry.resolve("Newport Beach")).thenReturn(Optional.of(strategy));
        when(spreadsheetParser.parse(file)).thenReturn(workbook("Newport Beach", "Replace DI bottles", "F6"));
        when(measurementBoundRepository.findByLocationId(9L)).thenReturn(measurementBounds());

        Graph waterQualityGraph = scatterGraph(18L, "Water Quality Compliance", "Newport Beach");
        Graph systemTypeGraph = scatterGraph(19L, "System Type Compliance", "Newport Beach");
        Graph totalNonConformancesGraph = pieGraph(20L, "Total Non-Conformances");
        Graph byFacilityGraph = barGraph(21L, "Non-Conformances", "By Facility");
        Graph turnaroundTimeGraph = barGraph(22L, "Non-Conformance Status", "Turnaround Time");
        Graph statusByFacilityGraph = verticalStatusByFacilityGraph(
            23L,
            "Non-Conformance Status",
            "By Facility",
            "#b91c1c",
            "#15803d"
        );
        List<Graph> graphs = List.of(
            waterQualityGraph,
            systemTypeGraph,
            totalNonConformancesGraph,
            byFacilityGraph,
            turnaroundTimeGraph,
            statusByFacilityGraph
        );
        when(locationGraphRepository.findByLocationIdWithGraph(9L)).thenReturn(graphs.stream()
            .map(graph -> locationGraph(9L, graph))
            .toList());

        LocationDashboardImportStrategy.CorrectiveActionDraft importedDraft = strategy.computeImport(
            workbook("Newport Beach", "Initial DI bottle change", "F5"),
            measurementBounds()
        ).correctiveActions().getFirst();
        ServiceEvent importedEvent = correctiveActionEvent(
            location,
            501L,
            importedDraft.title(),
            importedDraft.description(),
            LocalDate.parse("2025-08-01"),
            LocalDate.parse("2025-08-01"),
            LocalTime.of(23, 59, 59),
            ServiceEventStatus.OVERDUE
        );
        ServiceEvent legacyResolvedEvent = correctiveActionEvent(
            location,
            502L,
            "CA: Legacy Event",
            "Legacy corrective action without import metadata",
            LocalDate.parse("2025-07-01"),
            LocalDate.parse("2025-07-03"),
            LocalTime.NOON,
            ServiceEventStatus.COMPLETED
        );
        when(serviceEventRepository.findByLocation_IdAndCorrectiveActionTrueOrderByEventDateAscEventTimeAscIdAsc(9L))
            .thenReturn(List.of(importedEvent, legacyResolvedEvent));

        List<GraphResponse> responses = importService.importLocationDashboard(location, file);

        assertEquals(List.of(3L), findResponseByName(responses, "Total Non-Conformances").data().getFirst().get("values"));
        assertEquals(List.of(3L), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("x"));
        assertEquals(List.of("Newport Beach"), findResponseByNameAndTitle(responses, "Non-Conformances", "By Facility").data().getFirst().get("y"));

        Map<String, Object> activeByFacility = findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().getFirst();
        Map<String, Object> resolvedByFacility = findResponseByNameAndTitle(responses, "Non-Conformance Status", "By Facility").data().get(1);
        assertEquals(List.of("Newport Beach"), activeByFacility.get("x"));
        assertEquals(List.of("Newport Beach"), resolvedByFacility.get("x"));
        assertEquals(List.of(3L), activeByFacility.get("y"));
        assertEquals(List.of(0L), resolvedByFacility.get("y"));
    }

    private LocationDashboardImportService buildImportService() {
        return new LocationDashboardImportService(
            spreadsheetParser,
            strategyRegistry,
            measurementBoundRepository,
            locationGraphRepository,
            serviceEventRepository,
            new LocationDashboardMutationLockService(),
            Clock.fixed(Instant.parse("2025-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private ConfiguredLocationDashboardImportStrategy buildStrategy(
        List<LocationDashboardImportStrategyConfig.DerivedGraphConfig> derivedGraphs
    ) {
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
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.WATER_QUALITY_COMPLIANCE,
                        "newport-beach",
                        List.of("HPC", "Endotoxin"),
                        Map.of("HPC", "#1f77b4", "Endotoxin", "#2ca02c"),
                        "scatter"
                    ),
                    new LocationDashboardImportStrategyConfig.GraphConfig(
                        "system-type",
                        "System Type Compliance",
                        "Newport Beach",
                        LocationDashboardImportStrategyConfig.ImportType.SYSTEM_TYPE_COMPLIANCE,
                        "newport-beach",
                        List.of("Cooling Towers"),
                        Map.of("Cooling Towers", "#d62728"),
                        "scatter"
                    )
                ),
                derivedGraphs,
                List.of()
            )
        );
    }

    private List<LocationDashboardImportStrategyConfig.DerivedGraphConfig> allDerivedGraphDefinitions() {
        return List.of(
            derivedGraphDefinition("total-samples", "Total Number of Samples", null, LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_SAMPLES, "pie"),
            derivedGraphDefinition("total-non-conformances", "Total Non-Conformances", null, LocationDashboardImportStrategyConfig.DerivedGraphType.TOTAL_NON_CONFORMANCES, "pie"),
            derivedGraphDefinition("resolution-percent", "Percent Resolved", null, LocationDashboardImportStrategyConfig.DerivedGraphType.PERCENT_RESOLVED, "indicator"),
            derivedGraphDefinition("percent-conformance", "Percent Conformance", null, LocationDashboardImportStrategyConfig.DerivedGraphType.PERCENT_CONFORMANCE, "indicator"),
            derivedGraphDefinition("non-conformances-by-water-quality", "Non-Conformances", "By Water Quality Category", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCES_BY_CATEGORY, "bar"),
            derivedGraphDefinition("non-conformances-by-system-type", "Non-Conformances", "By Water System Type", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCES_BY_SYSTEM_TYPE, "bar"),
            derivedGraphDefinition("non-conformances-by-facility", "Non-Conformances", "By Facility", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCES_BY_FACILITY, "bar"),
            derivedGraphDefinition("non-conformance-status-by-turnaround-time", "Non-Conformance Status", "Turnaround Time", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_TURNAROUND_TIME, "bar"),
            derivedGraphDefinition("non-conformance-status-by-facility", "Non-Conformance Status", "By Facility", LocationDashboardImportStrategyConfig.DerivedGraphType.NON_CONFORMANCE_STATUS_BY_FACILITY, "bar")
        );
    }

    private LocationDashboardImportStrategyConfig.DerivedGraphConfig derivedGraphDefinition(
        String id,
        String name,
        String title,
        LocationDashboardImportStrategyConfig.DerivedGraphType derivedType,
        String graphType
    ) {
        return new LocationDashboardImportStrategyConfig.DerivedGraphConfig(id, name, title, derivedType, graphType);
    }

    private List<MeasurementBound> measurementBounds() {
        return List.of(
            measurementBound(1L, "HPC", null, null, null, null, null, null, null, new BigDecimal("10")),
            measurementBound(2L, "Endotoxin", null, null, null, null, null, null, null, new BigDecimal("1"))
        );
    }

    private MockMultipartFile dashboardFile() {
        return new MockMultipartFile(
            "file",
            "dashboard.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[] {1, 2, 3}
        );
    }

    private Location location(Long id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        return location;
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook(
        String locationTitle,
        String correctiveActionDescription,
        String firstCellReference
    ) {
        return workbookWithRawComment(
            locationTitle,
            "Test 1;350;CA;" + correctiveActionDescription,
            firstCellReference
        );
    }

    private LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbookWithRawComment(
        String locationTitle,
        String commentText,
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
                            commentText,
                            firstCellReference
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Endotoxin",
                            LocalDate.parse("2025-08-01"),
                            "0",
                            new BigDecimal("0"),
                            null,
                            "I5"
                        )
                    )
                ),
                new LocationDashboardSpreadsheetParser.ParsedDashboardRow(
                    6,
                    null,
                    null,
                    null,
                    "Recirc Line",
                    "CTI/514P",
                    List.of(
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "HPC",
                            LocalDate.parse("2025-08-01"),
                            "11",
                            new BigDecimal("11"),
                            null,
                            "F6"
                        ),
                        new LocationDashboardSpreadsheetParser.ParsedDashboardCell(
                            "Endotoxin",
                            LocalDate.parse("2025-08-01"),
                            "2",
                            new BigDecimal("2"),
                            null,
                            "I6"
                        )
                    )
                )
            )
        );
    }

    private Graph scatterGraph(Long id, String name, String titleText) {
        return graph(id, name, "scatter", Map.of("title", Map.of("text", titleText)), List.of());
    }

    private Graph pieGraph(Long id, String name) {
        return pieGraph(id, name, "#1f77b4");
    }

    private Graph pieGraph(Long id, String name, String color) {
        return graph(id, name, "pie", Map.of(), List.of(Map.of(
            "type", "pie",
            "name", "Trace 1",
            "hole", 0.72,
            "labels", List.of("fill"),
            "values", List.of(0),
            "marker", Map.of("color", color)
        )));
    }

    private Graph indicatorGraph(Long id, String name) {
        return indicatorGraph(id, name, "#1f77b4");
    }

    private Graph indicatorGraph(Long id, String name, String color) {
        return graph(id, name, "indicator", Map.of(), List.of(Map.of(
            "type", "indicator",
            "name", "Trace 1",
            "mode", "gauge+number",
            "value", 0,
            "number", Map.of("suffix", "%"),
            "gauge", Map.of(
                "axis", Map.of("range", List.of(0, 100)),
                "bar", Map.of("color", color)
            )
        )));
    }

    private Graph barGraph(Long id, String name, String titleText) {
        return graph(id, name, "bar", Map.of("title", Map.of("text", titleText)), List.of(Map.of(
            "type", "bar",
            "name", "Trace 1",
            "x", List.of(),
            "y", List.of(),
            "orientation", "h"
        )));
    }

    private Graph verticalStatusByFacilityGraph(
        Long id,
        String name,
        String titleText,
        String activeColor,
        String resolvedColor
    ) {
        return graph(id, name, "bar", Map.of("title", Map.of("text", titleText)), List.of(
            Map.of(
                "type", "bar",
                "name", "Active",
                "x", List.of(),
                "y", List.of(),
                "orientation", "v",
                "marker", Map.of("color", activeColor)
            ),
            Map.of(
                "type", "bar",
                "name", "Resolved",
                "x", List.of(),
                "y", List.of(),
                "orientation", "v",
                "marker", Map.of("color", resolvedColor)
            )
        ));
    }

    private Map<String, Object> scatterTrace(
        String name,
        List<String> x,
        List<Double> y,
        List<Map<String, Object>> customData
    ) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("type", "scatter");
        trace.put("name", name);
        trace.put("x", x);
        trace.put("y", y);
        trace.put("customdata", customData);
        trace.put("mode", "lines+markers");
        return trace;
    }

    private Graph legacyWaterQualityGraph(
        Long id,
        String name,
        String titleText,
        String traceName,
        List<String> x,
        List<Double> y,
        List<Map<String, Object>> customData
    ) {
        Graph graph = new Graph();
        graph.setId(id);
        graph.setName(name);
        graph.setGraphType("scatter");
        graph.setLayout(new LinkedHashMap<>(Map.of("title", Map.of("text", titleText))));
        graph.setConfig(Map.of());
        graph.setStyle(Map.of());
        graph.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        graph.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        GraphTrace trace = new GraphTrace();
        trace.setGraph(graph);
        trace.setTraceKey("trace-0");
        trace.setTraceName(traceName);
        trace.setTraceType("scatter");
        trace.setDataMode("time_series");
        trace.setTraceOrder(0);
        trace.setTraceConfig(new LinkedHashMap<>(Map.of(
            "mode", "lines+markers",
            "customdata", customData
        )));

        List<GraphTimeSeriesPoint> points = new java.util.ArrayList<>();
        for (int index = 0; index < x.size(); index += 1) {
            GraphTimeSeriesPoint point = new GraphTimeSeriesPoint();
            point.setGraphTrace(trace);
            point.setObservedAt(LocalDate.parse(x.get(index)).atStartOfDay().toInstant(ZoneOffset.UTC));
            point.setPointOrder(index);
            point.setYNumeric(BigDecimal.valueOf(y.get(index)));
            point.setPointMeta(Map.of("x", x.get(index)));
            points.add(point);
        }
        trace.setTimeSeriesPoints(points);
        graph.setGraphTraces(List.of(trace));
        return graph;
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

    private ServiceEvent correctiveActionEvent(
        Location location,
        Long id,
        String title,
        String description,
        LocalDate observedDate,
        LocalDate endDate,
        LocalTime endTime,
        ServiceEventStatus status
    ) {
        ServiceEvent serviceEvent = new ServiceEvent();
        serviceEvent.setId(id);
        serviceEvent.setLocation(location);
        serviceEvent.setCorrectiveAction(true);
        serviceEvent.setTitle(title);
        serviceEvent.setDescription(description);
        serviceEvent.setEventDate(observedDate);
        serviceEvent.setEventTime(LocalTime.MIDNIGHT);
        serviceEvent.setEndEventDate(endDate);
        serviceEvent.setEndEventTime(endTime);
        serviceEvent.setStatus(status);
        return serviceEvent;
    }

    private String correctiveActionDescription(
        String measurementName,
        LocalDate observedDate,
        String sublocation,
        String systemType,
        String actionText
    ) {
        return String.join("\n", List.of(
            "Measurement: " + measurementName,
            "Observed At: " + observedDate,
            "Sublocation: " + sublocation,
            "System: " + systemType,
            "",
            "CA: " + actionText
        ));
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
