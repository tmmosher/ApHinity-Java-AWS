package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationDashboardSpreadsheetUploadIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass1!";

    @Test
    void uploadLocationDashboardSpreadsheetPersistsGraphUpdatesAndCorrectiveActions() throws Exception {
        createUser("partner-dashboard-upload@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Newport Beach");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        Graph waterQualityGraph = createGraph("Water Quality Compliance", blankScatterData());
        Graph systemTypeGraph = createGraph("System Type Compliance", blankScatterData());
        Graph totalSamplesGraph = createGraph("Total Number of Samples", blankPieData());
        Graph totalNonConformancesGraph = createGraph("Total Non-Conformances", blankPieData());
        Graph activePercentGraph = createGraph("Active Non-Conformance Percent", blankPieData());
        Graph nonConformancesByFacilityGraph = createGraphWithLayout(
            "Non-Conformances",
            blankBarData(),
            Map.of("title", Map.of("text", "By Facility"))
        );
        addLocationGraph(location, waterQualityGraph);
        addLocationGraph(location, systemTypeGraph);
        addLocationGraph(location, totalSamplesGraph);
        addLocationGraph(location, totalNonConformancesGraph);
        addLocationGraph(location, activePercentGraph);
        addLocationGraph(location, nonConformancesByFacilityGraph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-upload@example.com", PASSWORD);

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/dashboard/spreadsheet-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "dashboard.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        createDashboardSpreadsheet("Newport Beach", "Drain Tank, install new DI bottles", 4)
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].name").value("Water Quality Compliance"))
            .andExpect(jsonPath("$[0].data[0].name").value("HPC"))
            .andExpect(jsonPath("$[0].data[0].y[0]").value(0))
            .andExpect(jsonPath("$[0].data[1].name").value("Endotoxin"))
            .andExpect(jsonPath("$[0].data[1].y[0]").value(0))
            .andExpect(jsonPath("$[1].name").value("System Type Compliance"))
            .andExpect(jsonPath("$[1].data[0].name").value("Cooling Towers"))
            .andExpect(jsonPath("$[1].data[0].y[0]").value(0))
            .andExpect(jsonPath("$[2].name").value("Total Number of Samples"))
            .andExpect(jsonPath("$[2].data[0].values[0]").value(6))
            .andExpect(jsonPath("$[3].name").value("Total Non-Conformances"))
            .andExpect(jsonPath("$[3].data[0].values[0]").value(1))
            .andExpect(jsonPath("$[4].name").value("Active Non-Conformance Percent"))
            .andExpect(jsonPath("$[4].data[0].values[0]").value(100))
            .andExpect(jsonPath("$[5].name").value("Non-Conformances"))
            .andExpect(jsonPath("$[5].data[0].x[0]").value(1))
            .andExpect(jsonPath("$[5].data[0].y[0]").value("Newport Beach"));

        Graph persistedWaterQualityGraph = reloadGraph(waterQualityGraph.getId());
        Graph persistedSystemTypeGraph = reloadGraph(systemTypeGraph.getId());
        Graph persistedTotalSamplesGraph = reloadGraph(totalSamplesGraph.getId());
        Graph persistedTotalNonConformancesGraph = reloadGraph(totalNonConformancesGraph.getId());
        Graph persistedActivePercentGraph = reloadGraph(activePercentGraph.getId());
        Graph persistedNonConformancesByFacilityGraph = reloadGraph(nonConformancesByFacilityGraph.getId());

        assertThat(graphData(persistedWaterQualityGraph))
            .extracting(trace -> trace.get("name"))
            .containsExactly("HPC", "Endotoxin", "pH");
        assertThat(graphData(persistedSystemTypeGraph))
            .extracting(trace -> trace.get("name"))
            .containsExactly("Cooling Towers");
        assertEquals(List.of(6L), graphData(persistedTotalSamplesGraph).getFirst().get("values"));
        assertEquals(List.of(1L), graphData(persistedTotalNonConformancesGraph).getFirst().get("values"));
        assertEquals(List.of(100L, 0L), graphData(persistedActivePercentGraph).getFirst().get("values"));
        assertEquals(List.of(1L), graphData(persistedNonConformancesByFacilityGraph).getFirst().get("x"));
        assertEquals(List.of("Newport Beach"), graphData(persistedNonConformancesByFacilityGraph).getFirst().get("y"));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(1, persistedEvents.size());
        assertTrue(persistedEvents.getFirst().isCorrectiveAction());
        assertTrue(persistedEvents.getFirst().getTitle().startsWith("CA: HPC 2025-08-01"));
        assertTrue(persistedEvents.getFirst().getTitle().length() <= 42);
        assertTrue(persistedEvents.getFirst().getDescription().contains("CA: Drain Tank, install new DI bottles"));
        assertEquals(ServiceEventStatus.OVERDUE, persistedEvents.getFirst().getStatus());
    }

    @Test
    void reuploadingShiftedSpreadsheetUpdatesExistingCorrectiveActionInsteadOfCreatingDuplicate() throws Exception {
        createUser("partner-dashboard-reupload@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Newport Beach");
        seedMeasurement(location, "HPC", new BigDecimal("10"));
        seedMeasurement(location, "Endotoxin", new BigDecimal("1"));

        Graph waterQualityGraph = createGraph("Water Quality Compliance", blankScatterData());
        Graph systemTypeGraph = createGraph("System Type Compliance", blankScatterData());
        addLocationGraph(location, waterQualityGraph);
        addLocationGraph(location, systemTypeGraph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-dashboard-reupload@example.com", PASSWORD);

        uploadDashboardSpreadsheet(location.getId(), authCookies, createDashboardSpreadsheet(
            "Newport Beach",
            "Drain Tank, install new DI bottles",
            4
        ));
        uploadDashboardSpreadsheet(location.getId(), authCookies, createDashboardSpreadsheet(
            "Newport Beach",
            "Replace DI bottles",
            5
        ));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(1, persistedEvents.size());
        assertTrue(persistedEvents.getFirst().getDescription().contains("CA: Replace DI bottles"));
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

    private void seedMeasurement(Location location, String measurementName, BigDecimal towersRangeMax) {
        jdbcTemplate.update(
            """
                insert into measurement_bounds (
                    measurement_name,
                    towers_range_max
                ) values (?, ?)
                """,
            measurementName,
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

    private List<Map<String, Object>> blankScatterData() {
        return List.of(Map.of(
            "type", "scatter",
            "name", "Trace 1",
            "x", List.of(),
            "y", List.of()
        ));
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

    private byte[] createDashboardSpreadsheet(
        String locationTitle,
        String correctiveActionDescription,
        int dataRowIndex
    ) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 10));

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue(locationTitle);

            Row dateRow = sheet.createRow(2);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            writeDateCells(dateRow, 5, repeatedDateColumns(), dateStyle);
            writeDateCells(dateRow, 8, repeatedDateColumns(), dateStyle);

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            Row dataRow = sheet.createRow(dataRowIndex);
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue(10);
            dataRow.createCell(7).setCellValue(10);
            dataRow.createCell(8).setCellValue(2);
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
}
