package com.aphinity.client_analytics_core.api.core;

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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

            Row dateRow = sheet.createRow(2);
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
