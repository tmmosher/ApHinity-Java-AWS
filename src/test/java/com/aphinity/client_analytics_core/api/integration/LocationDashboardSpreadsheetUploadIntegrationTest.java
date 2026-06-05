package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.correctiveAction;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.sample;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.workbookComment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationDashboardSpreadsheetUploadIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass12!";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void uploadLocationDashboardSpreadsheetReturnsPreviewWithoutPersistingGraphUpdatesOrCorrectiveActions() throws Exception {
        createUser("partner-dashboard-upload@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        HoagGraphFixture graphs = seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload@example.com", PASSWORD);

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createDashboardSpreadsheet("Hoag Hospital", "Drain Tank, install new DI bottles", 4)
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(17))
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[0].name").value("HPC"))
            .andExpect(jsonPath("$[0].data[0].y[0]").value(0))
            .andExpect(jsonPath("$[0].data[1].name").value("Endotoxin"))
            .andExpect(jsonPath("$[0].data[1].y[0]").value(2))
            .andExpect(jsonPath("$[1].name").value("System Type Conformance"))
            .andExpect(jsonPath("$[1].data.length()").value(1))
            .andExpect(jsonPath("$[1].data[0].name").value("Cooling Tower"))
            .andExpect(jsonPath("$[1].data[0].y[0]").value(2))
            .andExpect(jsonPath("$[2].name").value("Total Number of Samples"))
            .andExpect(jsonPath("$[2].data[0].values[0]").value(6))
            .andExpect(jsonPath("$[3].name").value("Total Non-Conformances"))
            .andExpect(jsonPath("$[3].data[0].values[0]").value(2))
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andExpect(jsonPath("$[4].data[0].value").value(0))
            .andExpect(jsonPath("$[5].name").value("Percent Conformance"))
            .andExpect(jsonPath("$[5].data[0].value").value(67))
            .andExpect(jsonPath("$[6].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[6].data[0].x[0]").value(2))
            .andExpect(jsonPath("$[6].data[0].y[0]").value("Endotoxin"))
            .andExpect(jsonPath("$[7].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[7].data[0].x[0]").value(2))
            .andExpect(jsonPath("$[7].data[0].y[0]").value("Cooling Tower"))
            .andExpect(jsonPath("$[8].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[8].data[0].x[0]").value(2))
            .andExpect(jsonPath("$[8].data[0].y[0]").value("Newport Beach"));

        Graph persistedWaterQualityGraph = reloadGraph(graphs.waterQualityGraph().getId());
        Graph persistedSystemTypeGraph = reloadGraph(graphs.systemTypeGraph().getId());
        Graph persistedTotalSamplesGraph = reloadGraph(graphs.totalSamplesGraph().getId());
        Graph persistedTotalNonConformancesGraph = reloadGraph(graphs.totalNonConformancesGraph().getId());
        Graph persistedResolutionPercentGraph = reloadGraph(graphs.resolutionPercentGraph().getId());
        Graph persistedPercentConformanceGraph = reloadGraph(graphs.percentConformanceGraph().getId());
        Graph persistedByFacilityGraph = reloadGraph(graphs.byFacilityGraph().getId());

        assertThat(graphData(persistedWaterQualityGraph))
            .extracting(trace -> trace.get("name"))
            .containsExactly("Trace 1");
        assertThat(graphData(persistedSystemTypeGraph))
            .extracting(trace -> trace.get("name"))
            .containsExactly("Trace 1");
        assertEquals(List.of(0L), graphData(persistedTotalSamplesGraph).getFirst().get("values"));
        assertEquals(List.of(0L), graphData(persistedTotalNonConformancesGraph).getFirst().get("values"));
        assertEquals(0L, ((Number) graphData(persistedResolutionPercentGraph).getFirst().get("value")).longValue());
        assertEquals(0L, ((Number) graphData(persistedPercentConformanceGraph).getFirst().get("value")).longValue());
        assertEquals(List.of(), graphData(persistedByFacilityGraph).getFirst().get("x"));
        assertEquals(List.of(), graphData(persistedByFacilityGraph).getFirst().get("y"));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(0, persistedEvents.size());

        var persistedSamples = locationDashboardSampleRepository.findByLocation_IdOrderByObservedDateAscIdAsc(location.getId());
        assertEquals(6, persistedSamples.size());
        assertEquals(2, persistedSamples.stream().filter(sample -> !sample.isCompliant()).count());
        assertEquals(0, persistedSamples.stream().filter(sample -> sample.isResolved()).count());
        assertThat(persistedSamples)
            .extracting(sample -> sample.getMeasurementName())
            .contains("HPC", "Endotoxin");
    }

    @Test
    void appliedSpreadsheetPreviewPersistsAndRefetchesUpdatedDashboardGraphs() throws Exception {
        createUser("partner-dashboard-upload-apply@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-apply@example.com", PASSWORD);

        MvcResult previewResult = mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createDashboardSpreadsheet("Hoag Hospital", "Drain Tank, install new DI bottles", 4)
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[1].y[0]").value(2))
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andExpect(jsonPath("$[4].data[0].value").value(0))
            .andReturn();

        List<Map<String, Object>> previewGraphs = objectMapper.readValue(
            previewResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        List<Map<String, Object>> graphUpdates = previewGraphs.stream()
            .map(this::toGraphUpdateRequest)
            .toList();

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("graphs", graphUpdates)))
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[1].name").value("Endotoxin"))
            .andExpect(jsonPath("$[0].data[1].y[0]").value(2))
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andExpect(jsonPath("$[4].data[0].value").value(0));

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs?monthRange=12", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[1].name").value("Endotoxin"))
            .andExpect(jsonPath("$[0].data[1].y[0]").value(2))
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andExpect(jsonPath("$[4].data[0].value").value(0));
    }

    @Test
    void uploadLocationDashboardSpreadsheetCountsOutOfSpecWorkbookCommentSamplesInDerivedNonConformanceGraphs() throws Exception {
        createUser("partner-dashboard-upload-workbook-comments@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-workbook-comments@example.com", PASSWORD);

        String workbookComment = workbookComment(new LocationDashboardCommentFixtures.WorkbookCommentSpec(
            "Cooling Tower Sample Port",
            sample(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-08-05"),
                "10 CFU.mL",
                new BigDecimal("10"),
                "CFU.mL"
            ),
            List.of(sample(
                LocalDate.parse("2025-08-15"),
                LocalDate.parse("2025-08-20"),
                "15 CFU.mL",
                new BigDecimal("15"),
                "CFU.mL",
                List.of(),
                List.of(correctiveAction("Disinfect and retest"))
            )),
            List.of(),
            List.of()
        ));

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createDashboardSpreadsheetWithRawComment("Hoag Hospital", workbookComment, 4)
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[3].name").value("Total Non-Conformances"))
            .andExpect(jsonPath("$[3].data[0].values[0]").value(3))
            .andExpect(jsonPath("$[6].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[6].data[0].x[0]").value(2))
            .andExpect(jsonPath("$[6].data[0].y[0]").value("Endotoxin"))
            .andExpect(jsonPath("$[6].data[0].x[1]").value(1))
            .andExpect(jsonPath("$[6].data[0].y[1]").value("HPC"))
            .andExpect(jsonPath("$[7].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[7].data[0].x[0]").value(3))
            .andExpect(jsonPath("$[7].data[0].y[0]").value("Cooling Tower"))
            .andExpect(jsonPath("$[8].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[8].data[0].x[0]").value(3))
            .andExpect(jsonPath("$[8].data[0].y[0]").value("Newport Beach"));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(0, persistedEvents.size());
    }

    @Test
    void uploadLocationDashboardSpreadsheetExampleTwoCoercesHoagSystemAliasesAndPopulatesWaterQualityGraphs() throws Exception {
        createUser("partner-dashboard-upload-example2@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedHoagMeasurement(location, "HPC");
        seedHoagMeasurement(location, "Endotoxin");
        seedHoagMeasurement(location, "Legionella");
        seedHoagMeasurement(location, "pH");
        seedHoagMeasurement(location, "Conductivity");
        seedHoagMeasurement(location, "Alkalinity");
        seedHoagMeasurement(location, "Hardness");

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-example2@example.com", PASSWORD);
        byte[] spreadsheet = readFixtureBytes("dashboard_upload_template_example_2.xlsx");

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard_upload_template_example_2.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(17))
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data.length()").value(7))
            .andExpect(jsonPath("$[0].data[0].name").value("HPC"))
            .andExpect(jsonPath("$[0].data[0].x[0]").exists())
            .andExpect(jsonPath("$[1].name").value("System Type Conformance"))
            .andExpect(jsonPath("$[1].data.length()").value(4))
            .andExpect(jsonPath("$[1].data[0].name").value("Utility SPD"))
            .andExpect(jsonPath("$[1].data[3].name").value("Cooling Tower"))
            .andExpect(jsonPath("$[2].name").value("Total Number of Samples"))
            .andExpect(jsonPath("$[2].data[0].values[0]").value(greaterThan(0)))
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andExpect(jsonPath("$[5].name").value("Percent Conformance"));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(0, persistedEvents.size());
    }

    @Test
    void appliedExampleTwoSpreadsheetPreviewRebuildsResolutionDerivedGraphsFromPersistedSampleFactsAfterRefetch() throws Exception {
        createUser("partner-dashboard-upload-example2-apply@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedHoagMeasurement(location, "HPC");
        seedHoagMeasurement(location, "Endotoxin");
        seedHoagMeasurement(location, "Legionella");
        seedHoagMeasurement(location, "pH");
        seedHoagMeasurement(location, "Conductivity");
        seedHoagMeasurement(location, "Alkalinity");
        seedHoagMeasurement(location, "Hardness");

        seedHoagStrategyGraphs(location);
        ServiceEvent persistedCorrectiveAction = createServiceEvent(
            location,
            "Existing corrective action",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2025-01-15"),
            LocalTime.NOON,
            LocalDate.parse("2025-01-20"),
            LocalTime.NOON,
            "Existing resolved action",
            ServiceEventStatus.COMPLETED
        );
        persistedCorrectiveAction.setCorrectiveAction(true);
        serviceEventRepository.saveAndFlush(persistedCorrectiveAction);

        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-example2-apply@example.com", PASSWORD);
        byte[] spreadsheet = readFixtureBytes("dashboard_upload_template_example_2.xlsx");

        MvcResult previewResult = mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard_upload_template_example_2.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[4].name").value("Percent Resolved"))
            .andReturn();

        List<Map<String, Object>> previewGraphs = objectMapper.readValue(
            previewResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );
        Map<String, Object> previewPercentResolved = findGraph(previewGraphs, "Percent Resolved", null);
        Map<String, Object> previewTotalSamples = findGraph(previewGraphs, "Total Number of Samples", null);
        Map<String, Object> previewTotalNonConformances = findGraph(previewGraphs, "Total Non-Conformances", null);
        Map<String, Object> previewTurnaround = findGraph(previewGraphs, "Non-Conformance Status", "Turnaround Time");
        Map<String, Object> previewStatusByFacility = findGraph(previewGraphs, "Non-Conformance Status", "By Facility");

        assertThat(((Number) firstTrace(previewPercentResolved).get("value")).intValue()).isGreaterThan(0);
        assertThat((List<?>) firstTrace(previewTotalSamples).get("values")).isNotEmpty();
        assertThat((List<?>) firstTrace(previewTotalNonConformances).get("values")).isNotEmpty();
        assertThat((List<?>) firstTrace(previewTurnaround).get("x")).isNotEmpty();
        assertThat((List<?>) firstTrace(previewStatusByFacility).get("x")).isNotEmpty();

        List<Map<String, Object>> graphUpdates = previewGraphs.stream()
            .map(this::toGraphUpdateRequest)
            .toList();

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("graphs", graphUpdates)))
            )
            .andExpect(status().isNoContent());

        MvcResult refetchResult = mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> refetchedGraphs = objectMapper.readValue(
            refetchResult.getResponse().getContentAsString(),
            new TypeReference<>() {
            }
        );

        assertThat(firstTrace(findGraph(refetchedGraphs, "Total Number of Samples", null)).get("values"))
            .isEqualTo(firstTrace(previewTotalSamples).get("values"));
        assertThat(firstTrace(findGraph(refetchedGraphs, "Total Non-Conformances", null)).get("values"))
            .isEqualTo(firstTrace(previewTotalNonConformances).get("values"));
        assertThat(firstTrace(findGraph(refetchedGraphs, "Percent Resolved", null)).get("value"))
            .isEqualTo(firstTrace(previewPercentResolved).get("value"));
        assertThat(firstTrace(findGraph(refetchedGraphs, "Non-Conformance Status", "Turnaround Time")).get("x"))
            .isEqualTo(firstTrace(previewTurnaround).get("x"));
        assertThat(firstTrace(findGraph(refetchedGraphs, "Non-Conformance Status", "By Facility")).get("x"))
            .isEqualTo(firstTrace(previewStatusByFacility).get("x"));
    }

    @Test
    void uploadLocationDashboardSpreadsheetExampleTwoReplacesNamedEmptyWaterQualityPlaceholderTraceData() throws Exception {
        createUser("partner-dashboard-upload-placeholders@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedHoagMeasurement(location, "HPC");
        seedHoagMeasurement(location, "Endotoxin");
        seedHoagMeasurement(location, "Legionella");
        seedHoagMeasurement(location, "pH");
        seedHoagMeasurement(location, "Conductivity");
        seedHoagMeasurement(location, "Alkalinity");
        seedHoagMeasurement(location, "Hardness");

        HoagGraphFixture graphs = seedHoagStrategyGraphs(location);
        graphs.waterQualityGraph().setData(namedEmptyScatterData(List.of(
            "HPC",
            "Endotoxin",
            "Legionella",
            "pH",
            "Conductivity",
            "Alkalinity",
            "Hardness"
        )));
        graphRepository.saveAndFlush(graphs.waterQualityGraph());

        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-placeholders@example.com", PASSWORD);
        byte[] spreadsheet = readFixtureBytes("dashboard_upload_template_example_2.xlsx");

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard_upload_template_example_2.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[0].name").value("HPC"))
            .andExpect(jsonPath("$[0].data[0].x[0]").exists())
            .andExpect(jsonPath("$[0].data[0].y[0]").isNumber());
    }

    @Test
    void uploadLocationDashboardSpreadsheetExampleTwoNormalizesMeasurementBoundNamesForWaterQualityGraphs() throws Exception {
        createUser("partner-dashboard-upload-measurement-names@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, " hpc ", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, "ENDOTOXIN", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, " legionella ", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, "PH", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, " conductivity ", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, "ALKALINITY", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));
        seedMeasurement(location, " hardness ", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"));

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload-measurement-names@example.com", PASSWORD);
        byte[] spreadsheet = readFixtureBytes("dashboard_upload_template_example_2.xlsx");

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard_upload_template_example_2.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[0].data[0].name").value("HPC"))
            .andExpect(jsonPath("$[0].data[0].x[0]").exists())
            .andExpect(jsonPath("$[0].data[0].y[0]").isNumber());
    }

    @Test
    void uploadLocationDashboardSpreadsheetAcceptsShiftedHeaderSpacing() throws Exception {
        createUser("partner-dashboard-shifted@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-shifted@example.com", PASSWORD);

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createShiftedDashboardSpreadsheet("Hoag Hospital", "Drain Tank, install new DI bottles")
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(17))
            .andExpect(jsonPath("$[0].name").value("Water Quality Conformance"))
            .andExpect(jsonPath("$[2].name").value("Total Number of Samples"))
            .andExpect(jsonPath("$[2].data[0].values[0]").value(6));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(0, persistedEvents.size());
    }

    @Test
    void reuploadingShiftedSpreadsheetDoesNotPersistCorrectiveActions() throws Exception {
        createUser("partner-dashboard-reupload@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Hoag Hospital");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        seedHoagStrategyGraphs(location);
        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-reupload@example.com", PASSWORD);

        uploadDashboardSpreadsheet(location.getId(), authCookies, createDashboardSpreadsheet(
            "Hoag Hospital",
            "Drain Tank, install new DI bottles",
            4
        ));
        uploadDashboardSpreadsheet(location.getId(), authCookies, createDashboardSpreadsheet(
            "Hoag Hospital",
            "Replace DI bottles",
            5
        ));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(0, persistedEvents.size());
    }

    private void uploadDashboardSpreadsheet(Long locationId, AuthCookies authCookies, byte[] spreadsheet) throws Exception {
        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", locationId)
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk());
    }

    private byte[] readFixtureBytes(String fileName) throws IOException {
        try (var inputStream = new ClassPathResource(fileName).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private void seedMeasurement(Location location, String measurementName, BigDecimal towersRangeMax) {
        seedMeasurement(location, measurementName, null, null, null, towersRangeMax);
    }

    private void seedHoagMeasurement(Location location, String measurementName) {
        seedMeasurement(
            location,
            measurementName,
            new BigDecimal("100"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new BigDecimal("100")
        );
    }

    private void seedMeasurement(
        Location location,
        String measurementName,
        BigDecimal criticalRangeMax,
        BigDecimal utilityRangeMax,
        BigDecimal potableRangeMax,
        BigDecimal towersRangeMax
    ) {
        jdbcTemplate.update(
            """
                insert into measurement_bounds (
                    measurement_name,
                    critical_range_max,
                    utility_range_max,
                    potable_range_max,
                    towers_range_max
                ) values (?, ?, ?, ?, ?)
                """,
            measurementName,
            criticalRangeMax,
            utilityRangeMax,
            potableRangeMax,
            towersRangeMax
        );
        Long measurementId = jdbcTemplate.queryForObject(
            "select id from measurement_bounds where measurement_name = ?",
            Long.class,
            measurementName
        );
        jdbcTemplate.update(
            "insert into location_measurements (location_id, measurement_id) values (?, ?)",
            location.getId(),
            measurementId
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphData(Graph graph) {
        return (List<Map<String, Object>>) graph.getData();
    }

    private Map<String, Object> firstTrace(Map<String, Object> graph) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) graph.get("data");
        return data.getFirst();
    }

    private Map<String, Object> findGraph(List<Map<String, Object>> graphs, String name, String title) {
        return graphs.stream()
            .filter(graph -> Objects.equals(name, graph.get("name")))
            .filter(graph -> Objects.equals(title, graphTitle(graph)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected graph not found: " + name + " / " + title));
    }

    @SuppressWarnings("unchecked")
    private String graphTitle(Map<String, Object> graph) {
        Object layoutValue = graph.get("layout");
        if (!(layoutValue instanceof Map<?, ?> layout)) {
            return null;
        }
        Object titleValue = layout.get("title");
        if (!(titleValue instanceof Map<?, ?> title)) {
            return null;
        }
        Object textValue = title.get("text");
        return textValue instanceof String text ? text : null;
    }

    private Map<String, Object> toGraphUpdateRequest(Map<String, Object> graph) {
        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("graphId", graph.get("id"));
        update.put("data", graph.get("data"));
        update.put("layout", graph.get("layout"));
        update.put("config", graph.get("config"));
        update.put("style", graph.get("style"));
        update.put("expectedUpdatedAt", graph.get("updatedAt"));
        return update;
    }

    private List<Map<String, Object>> blankScatterData() {
        return List.of(Map.of(
            "type", "scatter",
            "name", "Trace 1",
            "x", List.of(),
            "y", List.of()
        ));
    }

    private List<Map<String, Object>> namedEmptyScatterData(List<String> traceNames) {
        return traceNames.stream()
            .map(traceName -> Map.<String, Object>of(
                "type", "scatter",
                "name", traceName,
                "x", List.of(),
                "y", List.of(),
                "mode", "lines+markers",
                "line", Map.of("color", "#1f77b4", "width", 2, "shape", "hv", "smoothing", 0.3d),
                "marker", Map.of("size", 6)
            ))
            .toList();
    }

    private List<Map<String, Object>> blankPieData() {
        return List.of(Map.of(
            "type", "pie",
            "name", "Trace 1",
            "hole", 0.72,
            "labels", List.of("fill"),
            "values", List.of(0)
        ));
    }

    private List<Map<String, Object>> blankIndicatorData() {
        return List.of(Map.of(
            "type", "indicator",
            "name", "Trace 1",
            "mode", "gauge+number",
            "value", 0,
            "number", Map.of("suffix", "%"),
            "gauge", Map.of("axis", Map.of("range", List.of(0, 100)))
        ));
    }

    private List<Map<String, Object>> blankBarData() {
        return List.of(Map.of(
            "type", "bar",
            "name", "Trace 1",
            "x", List.of(),
            "y", List.of(),
            "orientation", "h"
        ));
    }

    private Graph createGraphWithLayout(String name, Object data, Map<String, Object> layout) {
        Graph graph = createGraph(name, data);
        graph.setLayout(layout);
        return graphRepository.saveAndFlush(graph);
    }

    private Graph createGraphWithTitle(String name, String titleText, Object data) {
        return createGraphWithLayout(name, data, Map.of("title", Map.of("text", titleText)));
    }

    private HoagGraphFixture seedHoagStrategyGraphs(Location location) {
        Graph waterQualityGraph = createGraphWithTitle("Water Quality Conformance", "Newport Beach", blankScatterData());
        Graph systemTypeGraph = createGraphWithTitle("System Type Conformance", "Newport Beach", blankScatterData());
        Graph totalSamplesGraph = createGraph("Total Number of Samples", blankPieData());
        Graph totalNonConformancesGraph = createGraph("Total Non-Conformances", blankPieData());
        Graph resolutionPercentGraph = createGraph("Percent Resolved", blankIndicatorData());
        Graph percentConformanceGraph = createGraph("Percent Conformance", blankIndicatorData());
        Graph byWaterQualityGraph = createGraphWithLayout(
            "Non-Conformances",
            blankBarData(),
            Map.of("title", Map.of("text", "By Water Quality Category"))
        );
        Graph bySystemTypeGraph = createGraphWithLayout(
            "Non-Conformances",
            blankBarData(),
            Map.of("title", Map.of("text", "By Water System Type"))
        );
        Graph byFacilityGraph = createGraphWithLayout(
            "Non-Conformances",
            blankBarData(),
            Map.of("title", Map.of("text", "By Facility"))
        );
        Graph turnaroundTimeGraph = createGraphWithLayout(
            "Non-Conformance Status",
            blankBarData(),
            Map.of("title", Map.of("text", "Turnaround Time"))
        );
        Graph statusByFacilityGraph = createGraphWithLayout(
            "Non-Conformance Status",
            blankBarData(),
            Map.of("title", Map.of("text", "By Facility"))
        );

        addLocationGraph(location, waterQualityGraph);
        addLocationGraph(location, systemTypeGraph);
        addLocationGraph(location, totalSamplesGraph);
        addLocationGraph(location, totalNonConformancesGraph);
        addLocationGraph(location, resolutionPercentGraph);
        addLocationGraph(location, percentConformanceGraph);
        addLocationGraph(location, byWaterQualityGraph);
        addLocationGraph(location, bySystemTypeGraph);
        addLocationGraph(location, byFacilityGraph);
        addLocationGraph(location, turnaroundTimeGraph);
        addLocationGraph(location, statusByFacilityGraph);
        addLocationGraph(location, createGraphWithTitle("Water Quality Conformance", "Irvine", blankScatterData()));
        addLocationGraph(location, createGraphWithTitle("System Type Conformance", "Irvine", blankScatterData()));
        addLocationGraph(location, createGraphWithTitle("Water Quality Conformance", "16405 Irvine", blankScatterData()));
        addLocationGraph(location, createGraphWithTitle("System Type Conformance", "16405 Irvine", blankScatterData()));
        addLocationGraph(location, createGraphWithTitle("Water Quality Conformance", "Surgical Pavilion", blankScatterData()));
        addLocationGraph(location, createGraphWithTitle("System Type Conformance", "Surgical Pavilion", blankScatterData()));

        return new HoagGraphFixture(
            waterQualityGraph,
            systemTypeGraph,
            totalSamplesGraph,
            totalNonConformancesGraph,
            resolutionPercentGraph,
            percentConformanceGraph,
            byFacilityGraph
        );
    }

    private byte[] createDashboardSpreadsheet(
        String locationTitle,
        String correctiveActionDescription,
        int dataRowIndex
    ) throws IOException {
        return createDashboardSpreadsheetWithRawComment(
            locationTitle,
            "Test 1;350;CA;" + correctiveActionDescription,
            dataRowIndex
        );
    }

    private byte[] createDashboardSpreadsheetWithRawComment(
        String locationTitle,
        String commentText,
        int dataRowIndex
    ) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Validation");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 10));

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue(locationTitle);
            titleRow.createCell(5).setCellValue("Data Range (Ignored)");
            titleRow.createCell(8).setCellValue("Data Range (Ignored)");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 8, 10));

            Row dateRow = sheet.createRow(2);
            dateRow.createCell(0).setCellValue("Subtitle (Ignored)");
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            writeDateCells(dateRow, 5, repeatedDateColumns(), dateStyle);
            writeDateCells(dateRow, 8, repeatedDateColumns(), dateStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 3));

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            Row dataRow = sheet.createRow(dataRowIndex);
            dataRow.createCell(0).setCellValue("NB");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue(10);
            dataRow.createCell(7).setCellValue(10);
            dataRow.createCell(8).setCellValue(0.5);
            dataRow.createCell(9).setCellValue(2);
            dataRow.createCell(10).setCellValue(2);

            addComment(
                workbook,
                sheet,
                dataRow.getCell(5),
                commentText
            );

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createShiftedDashboardSpreadsheet(
        String locationTitle,
        String correctiveActionDescription
    ) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Validation");

            Row metricRow = sheet.createRow(4);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 8, 10));

            Row spacerRow = sheet.createRow(5);
            spacerRow.createCell(5).setCellValue("Critical <10 CFU/ml");
            spacerRow.createCell(8).setCellValue("Critical <10 CFU/ml");
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 8, 10));

            Row dateRow = sheet.createRow(6);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            writeDateCells(dateRow, 5, repeatedDateColumns(), dateStyle);
            writeDateCells(dateRow, 8, repeatedDateColumns(), dateStyle);

            Row titleRow = sheet.createRow(9);
            titleRow.createCell(0).setCellValue(locationTitle);
            sheet.addMergedRegion(new CellRangeAddress(9, 9, 0, 3));

            Row subtitleRow = sheet.createRow(10);
            subtitleRow.createCell(0).setCellValue("Subtitle (Ignored)");
            sheet.addMergedRegion(new CellRangeAddress(10, 10, 0, 3));

            Row headerRow = sheet.createRow(11);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            Row dataRow = sheet.createRow(12);
            dataRow.createCell(0).setCellValue("NB");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue(10);
            dataRow.createCell(7).setCellValue(10);
            dataRow.createCell(8).setCellValue(0.5);
            dataRow.createCell(9).setCellValue(2);
            dataRow.createCell(10).setCellValue(2);

            addComment(
                workbook,
                sheet,
                dataRow.getCell(5),
                "Test 1;350;CA;" + correctiveActionDescription
            );

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeDateCells(Row row, int startColumnIndex, List<LocalDate> dates, CellStyle dateStyle) {
        for (int offset = 0; offset < dates.size(); offset += 1) {
            Cell cell = row.createCell(startColumnIndex + offset);
            cell.setCellValue(java.sql.Date.valueOf(dates.get(offset)));
            cell.setCellStyle(dateStyle);
        }
    }

    private List<LocalDate> repeatedDateColumns() {
        return List.of(
            LocalDate.parse("2025-08-01"),
            LocalDate.parse("2025-08-01"),
            LocalDate.parse("2025-08-01")
        );
    }

    private void addComment(XSSFWorkbook workbook, Sheet sheet, Cell cell, String value) {
        CreationHelper creationHelper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = creationHelper.createClientAnchor();
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(creationHelper.createRichTextString(value));
        cell.setCellComment(comment);
    }

    private record HoagGraphFixture(
        Graph waterQualityGraph,
        Graph systemTypeGraph,
        Graph totalSamplesGraph,
        Graph totalNonConformancesGraph,
        Graph resolutionPercentGraph,
        Graph percentConformanceGraph,
        Graph byFacilityGraph
    ) {
    }
}
