package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardSpreadsheetParser;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardSpreadsheetParserTest {
    private final LocationDashboardSpreadsheetParser parser = new LocationDashboardSpreadsheetParser();

    @Test
    void parseReadsMetricBlocksDatesAndCommentsFromDashboardWorkbook() throws IOException {
        MockMultipartFile file = createWorkbook();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = parser.parse(file);

        assertEquals("Newport Beach", workbook.locationTitle());
        assertEquals(1, workbook.rows().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardRow row = workbook.rows().getFirst();
        assertEquals("Newport Beach", row.facility());
        assertEquals("Hospital", row.building());
        assertEquals("Cooling Towers", row.system());
        assertEquals("Recirc Line", row.pointOfUse());
        assertEquals("CTI/514P", row.basis());
        assertEquals(9, row.cells().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardCell firstCell = row.cells().getFirst();
        assertEquals("HPC", firstCell.metricName());
        assertEquals(LocalDate.parse("2025-08-01"), firstCell.observedDate());
        assertEquals("10", firstCell.rawValue());
        assertEquals(new BigDecimal("10.0"), firstCell.numericValue());
        assertEquals("Test 1;350;CA;Drain Tank, install new DI bottles; Test 2;10", firstCell.commentText());

        LocationDashboardSpreadsheetParser.ParsedDashboardCell secondCell = row.cells().get(1);
        assertEquals("HPC", secondCell.metricName());
        assertEquals(LocalDate.parse("2025-09-01"), secondCell.observedDate());
        assertEquals("<1", secondCell.rawValue());
        assertEquals(new BigDecimal("1"), secondCell.numericValue());
    }

    @Test
    void parseAcceptsDashboardHeadersWithExtraSpacerRows() throws IOException {
        MockMultipartFile file = createShiftedWorkbook();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = parser.parse(file);

        assertEquals("Newport Beach", workbook.locationTitle());
        assertEquals(1, workbook.rows().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardRow row = workbook.rows().getFirst();
        assertEquals("Newport Beach", row.facility());
        assertEquals("Hospital", row.building());
        assertEquals("Cooling Towers", row.system());
        assertEquals("Recirc Line", row.pointOfUse());
        assertEquals("CTI/514P", row.basis());
        assertEquals(9, row.cells().size());
        assertEquals("HPC", row.cells().getFirst().metricName());
        assertEquals(LocalDate.parse("2025-08-01"), row.cells().getFirst().observedDate());
    }

    @Test
    void parseAcceptsShortDashboardHeadersBeyondInitialScanLimit() throws IOException {
        MockMultipartFile file = createShortHeaderWorkbook();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = parser.parse(file);

        assertEquals("Newport Beach", workbook.locationTitle());
        assertEquals(1, workbook.rows().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardRow row = workbook.rows().getFirst();
        assertEquals("Newport Beach", row.facility());
        assertEquals("Hospital", row.building());
        assertEquals("Cooling Towers", row.system());
        assertEquals("Recirc Line", row.pointOfUse());
        assertEquals("CTI/514P", row.basis());
        assertEquals(9, row.cells().size());
        assertEquals("HPC", row.cells().getFirst().metricName());
        assertEquals(LocalDate.parse("2025-08-01"), row.cells().getFirst().observedDate());
    }

    @Test
    void parseAcceptsThresholdValuesWithoutLeadingZero() throws IOException {
        MockMultipartFile file = createLeadingDecimalWorkbook();

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = parser.parse(file);

        assertEquals("Newport Beach", workbook.locationTitle());
        assertEquals(1, workbook.rows().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardRow row = workbook.rows().getFirst();
        assertEquals(1, row.cells().size());

        LocationDashboardSpreadsheetParser.ParsedDashboardCell cell = row.cells().getFirst();
        assertEquals("HPC", cell.metricName());
        assertEquals(LocalDate.parse("2025-08-01"), cell.observedDate());
        assertEquals("<.05", cell.rawValue());
        assertEquals(new BigDecimal("0.05"), cell.numericValue());
    }

    @Test
    void parseCoercesNdToZeroAndSkipsNtCells() throws IOException {
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook ndWorkbook = parser.parse(
            createSemanticValueWorkbook("ND")
        );
        LocationDashboardSpreadsheetParser.ParsedDashboardCell ndCell = ndWorkbook.rows().getFirst().cells().getFirst();
        assertEquals("ND", ndCell.rawValue());
        assertEquals(new BigDecimal("0"), ndCell.numericValue());

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook ntWorkbook = parser.parse(
            createSemanticValueWorkbook("NT")
        );
        assertTrue(ntWorkbook.rows().getFirst().cells().isEmpty());
    }

    @Test
    void parseReadsMigratedStructuredCommentsFromDataUploadWorkbook() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "data_upload_test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            Files.readAllBytes(Path.of("data_upload_test.xlsx"))
        );

        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook = parser.parse(file);

        LocationDashboardSpreadsheetParser.ParsedDashboardCell commentCell = workbook.rows().stream()
            .flatMap(row -> row.cells().stream())
            .filter(cell -> "W39".equals(cell.cellReference()))
            .findFirst()
            .orElseThrow();

        assertTrue(commentCell.commentText() != null && commentCell.commentText().startsWith("{"));
        assertTrue(commentCell.commentText().contains("\"schema\": \"aphinity.location-dashboard.comment.v1\""));
        assertTrue(commentCell.commentText().contains("\"followUpSamples\""));
        assertTrue(commentCell.commentText().contains("\"correctiveActions\""));
    }

    @Test
    void parseRejectsTemplateShellWithoutRealDates() throws IOException {
        MockMultipartFile file = createTemplateShellWorkbook();

        ApiClientException error = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("Spreadsheet is missing the date row.", error.getMessage());
    }

    private MockMultipartFile createWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            metricRow.createCell(11).setCellValue("pH");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 11, 13));

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue("Newport Beach");
            titleRow.createCell(5).setCellValue("Data Range (Ignored)");
            titleRow.createCell(8).setCellValue("Data Range (Ignored)");
            titleRow.createCell(11).setCellValue("Data Range (Ignored)");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 11, 13));

            Row dateRow = sheet.createRow(2);
            dateRow.createCell(0).setCellValue("Subtitle (Ignored)");
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            List<LocalDate> dates = List.of(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-09-01"),
                LocalDate.parse("2025-10-01")
            );
            writeDateCells(dateRow, 5, dates, dateStyle);
            writeDateCells(dateRow, 8, dates, dateStyle);
            writeDateCells(dateRow, 11, dates, dateStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 3));

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            Row dataRow = sheet.createRow(4);
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue("<1");
            dataRow.createCell(7).setCellValue(3);
            dataRow.createCell(8).setCellValue(0);
            dataRow.createCell(9).setCellValue(2);
            dataRow.createCell(10).setCellValue(1);
            dataRow.createCell(11).setCellValue(4);
            dataRow.createCell(12).setCellValue(5);
            dataRow.createCell(13).setCellValue(6);

            addComment(
                workbook,
                sheet,
                dataRow.getCell(5),
                "Test 1;350;CA;Drain Tank, install new DI bottles; Test 2;10"
            );

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createShiftedWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(4);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            metricRow.createCell(11).setCellValue("pH");
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 13));

            Row criteriaRow = sheet.createRow(5);
            criteriaRow.createCell(5).setCellValue("(Critical <10 CFU/ml, Utility <500 CFU/ml)");
            criteriaRow.createCell(8).setCellValue("(Critical <10 CFU/ml)");
            criteriaRow.createCell(11).setCellValue("(Critical 5.0-7.5, Utility 6.5-9.5)");
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 13));

            Row dateRow = sheet.createRow(6);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            List<LocalDate> dates = List.of(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-09-01"),
                LocalDate.parse("2025-10-01")
            );
            writeDateCells(dateRow, 5, dates, dateStyle);
            writeDateCells(dateRow, 8, dates, dateStyle);
            writeDateCells(dateRow, 11, dates, dateStyle);

            Row titleRow = sheet.createRow(9);
            titleRow.createCell(0).setCellValue("Newport Beach");
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
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue("<1");
            dataRow.createCell(7).setCellValue(3);
            dataRow.createCell(8).setCellValue(0);
            dataRow.createCell(9).setCellValue(2);
            dataRow.createCell(10).setCellValue(1);
            dataRow.createCell(11).setCellValue(4);
            dataRow.createCell(12).setCellValue(5);
            dataRow.createCell(13).setCellValue(6);

            addComment(
                workbook,
                sheet,
                dataRow.getCell(5),
                "Test 1;350;CA;Drain Tank, install new DI bottles; Test 2;10"
            );

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createShortHeaderWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(4);
            metricRow.createCell(5).setCellValue("HPC");
            metricRow.createCell(8).setCellValue("Endotoxin");
            metricRow.createCell(11).setCellValue("pH");
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 13));

            Row criteriaRow = sheet.createRow(5);
            criteriaRow.createCell(5).setCellValue("(Critical <10 CFU/ml, Utility <500 CFU/ml)");
            criteriaRow.createCell(8).setCellValue("(Critical <10 CFU/ml)");
            criteriaRow.createCell(11).setCellValue("(Critical 5.0-7.5, Utility 6.5-9.5)");
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 13));

            Row dateRow = sheet.createRow(6);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            List<LocalDate> dates = List.of(
                LocalDate.parse("2025-08-01"),
                LocalDate.parse("2025-09-01"),
                LocalDate.parse("2025-10-01")
            );
            writeDateCells(dateRow, 5, dates, dateStyle);
            writeDateCells(dateRow, 8, dates, dateStyle);
            writeDateCells(dateRow, 11, dates, dateStyle);

            Row titleRow = sheet.createRow(21);
            titleRow.createCell(0).setCellValue("Newport Beach");
            sheet.addMergedRegion(new CellRangeAddress(21, 21, 0, 3));

            Row subtitleRow = sheet.createRow(22);
            subtitleRow.createCell(0).setCellValue("Subtitle (Ignored)");
            sheet.addMergedRegion(new CellRangeAddress(22, 22, 0, 3));

            Row headerRow = sheet.createRow(23);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg");
            headerRow.createCell(2).setCellValue("System");
            headerRow.createCell(3).setCellValue("Point of Use");
            headerRow.createCell(4).setCellValue("Basis");

            Row dataRow = sheet.createRow(24);
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(10);
            dataRow.createCell(6).setCellValue("<1");
            dataRow.createCell(7).setCellValue(3);
            dataRow.createCell(8).setCellValue(0);
            dataRow.createCell(9).setCellValue(2);
            dataRow.createCell(10).setCellValue(1);
            dataRow.createCell(11).setCellValue(4);
            dataRow.createCell(12).setCellValue(5);
            dataRow.createCell(13).setCellValue(6);

            addComment(
                workbook,
                sheet,
                dataRow.getCell(5),
                "Test 1;350;CA;Drain Tank, install new DI bottles; Test 2;10"
            );

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createTemplateShellWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("Metric 1");
            metricRow.createCell(8).setCellValue("Metric 2");
            metricRow.createCell(11).setCellValue("Metric 3");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 11, 13));

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue("Title of Location");
            titleRow.createCell(5).setCellValue("Data Range (Ignored)");
            titleRow.createCell(8).setCellValue("Data Range (Ignored)");
            titleRow.createCell(11).setCellValue("Data Range (Ignored)");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 11, 13));

            Row labelRow = sheet.createRow(2);
            labelRow.createCell(0).setCellValue("Subtitle (Ignored)");
            labelRow.createCell(5).setCellValue("Date 1");
            labelRow.createCell(6).setCellValue("Date 2");
            labelRow.createCell(7).setCellValue("Date 3");
            labelRow.createCell(8).setCellValue("Date 1");
            labelRow.createCell(9).setCellValue("Date 2");
            labelRow.createCell(10).setCellValue("Date 3");
            labelRow.createCell(11).setCellValue("Date 1");
            labelRow.createCell(12).setCellValue("Date 2");
            labelRow.createCell(13).setCellValue("Date 3");
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 3));

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createLeadingDecimalWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("HPC");

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue("Newport Beach");

            Row dateRow = sheet.createRow(2);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            Cell dateCell = dateRow.createCell(5);
            dateCell.setCellValue(java.sql.Date.valueOf(LocalDate.parse("2025-08-01")));
            dateCell.setCellStyle(dateStyle);

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg (Collated if unrecognized)");
            headerRow.createCell(2).setCellValue("System (Collated if unrecognized)");
            headerRow.createCell(3).setCellValue("Point of Use (Ignored)");
            headerRow.createCell(4).setCellValue("Basis (ignored)");

            Row dataRow = sheet.createRow(4);
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue("<.05");

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createSemanticValueWorkbook(String value) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dashboard");

            Row metricRow = sheet.createRow(0);
            metricRow.createCell(5).setCellValue("HPC");

            Row titleRow = sheet.createRow(1);
            titleRow.createCell(0).setCellValue("Newport Beach");

            Row dateRow = sheet.createRow(2);
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            Cell dateCell = dateRow.createCell(5);
            dateCell.setCellValue(java.sql.Date.valueOf(LocalDate.parse("2025-08-01")));
            dateCell.setCellStyle(dateStyle);

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Facility");
            headerRow.createCell(1).setCellValue("Bldg");
            headerRow.createCell(2).setCellValue("System");
            headerRow.createCell(3).setCellValue("Point of Use");
            headerRow.createCell(4).setCellValue("Basis");

            Row dataRow = sheet.createRow(4);
            dataRow.createCell(0).setCellValue("Newport Beach");
            dataRow.createCell(1).setCellValue("Hospital");
            dataRow.createCell(2).setCellValue("Cooling Towers");
            dataRow.createCell(3).setCellValue("Recirc Line");
            dataRow.createCell(4).setCellValue("CTI/514P");
            dataRow.createCell(5).setCellValue(value);

            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                "dashboard.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private void writeDateCells(Row row, int startColumnIndex, List<LocalDate> dates, CellStyle dateStyle) {
        for (int offset = 0; offset < dates.size(); offset += 1) {
            Cell cell = row.createCell(startColumnIndex + offset);
            cell.setCellValue(java.sql.Date.valueOf(dates.get(offset)));
            cell.setCellStyle(dateStyle);
        }
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
