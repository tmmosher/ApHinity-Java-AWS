package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceCalendarSpreadsheetParser;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceCalendarSpreadsheetParserTest {
    private final ServiceCalendarSpreadsheetParser parser = new ServiceCalendarSpreadsheetParser();

    @Test
    void parseReadsTimedRowsFromSpreadsheet() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"),
            List.of("Pump visit", "Inspect pump pressure", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Partner", "Current")
        );

        List<ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow> rows = parser.parse(file);

        assertEquals(1, rows.size());
        LocationEventRequest request = rows.getFirst().request();
        assertEquals("Pump visit", request.title());
        assertEquals("Inspect pump pressure", request.description());
        assertEquals(ServiceEventResponsibility.PARTNER, request.responsibility());
        assertEquals(LocalDate.parse("2026-04-14"), request.date());
        assertEquals(LocalTime.parse("09:15:00"), request.time());
        assertEquals(LocalDate.parse("2026-04-14"), request.endDate());
        assertEquals(LocalTime.parse("11:45:00"), request.endTime());
        assertEquals(ServiceEventStatus.CURRENT, request.status());
    }

    @Test
    void parseReadsAllDayRowsAndDefaultsEndDate() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Site shutdown", "", "2026-04-20", "", "", "", "True", "Client")
        );

        List<ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow> rows = parser.parse(file);

        LocationEventRequest request = rows.getFirst().request();
        assertEquals(LocalDate.parse("2026-04-20"), request.date());
        assertEquals(LocalDate.parse("2026-04-20"), request.endDate());
        assertEquals(LocalTime.MIDNIGHT, request.time());
        assertEquals(LocalTime.parse("23:59:59"), request.endTime());
        assertEquals(ServiceEventResponsibility.CLIENT, request.responsibility());
    }

    @Test
    void parseRejectsInvalidResponsibilityValues() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Vendor")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: Responsibility must be Client or Partner.", ex.getMessage());
    }

    @Test
    void parseRejectsImpossibleCalendarDates() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Pump visit", "", "2/31/2026", "2026-04-14", "09:15", "11:45", "False", "Partner")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: Start Date is invalid.", ex.getMessage());
    }

    @Test
    void parseReadsTimedRowsThatSpanIntoTheNextDay() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"),
            List.of("Overnight visit", "Inspect overnight system behavior", "2026-04-14", "2026-04-15", "22:15", "01:30", "False", "Partner", "")
        );

        List<ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow> rows = parser.parse(file);

        LocationEventRequest request = rows.getFirst().request();
        assertEquals(LocalDate.parse("2026-04-14"), request.date());
        assertEquals(LocalTime.parse("22:15:00"), request.time());
        assertEquals(LocalDate.parse("2026-04-15"), request.endDate());
        assertEquals(LocalTime.parse("01:30:00"), request.endTime());
        assertEquals(ServiceEventStatus.UPCOMING, request.status());
    }

    @Test
    void parseRejectsRowsWhenEndDateIsBeforeStartDate() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Backwards visit", "", "2026-04-14", "2026-04-13", "09:15", "11:45", "False", "Partner")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: End date and time must be on or after the start date and time.", ex.getMessage());
    }

    @Test
    void parseRejectsRowsWhenEndTimeIsBeforeStartTimeOnTheSameDay() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Backwards time visit", "", "2026-04-14", "2026-04-14", "11:45", "09:15", "False", "Partner")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: End date and time must be on or after the start date and time.", ex.getMessage());
    }

    @Test
    void parseRejectsTimedRowsWithoutStartTime() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Missing time visit", "", "2026-04-14", "2026-04-14", "", "11:45", "False", "Partner")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: Start Time is required.", ex.getMessage());
    }

    @Test
    void parseRejectsInvalidStatusValues() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status"),
            List.of("Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False", "Partner", "Delayed")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_row_invalid", ex.getCode());
        assertEquals("Row 2: Status must be Upcoming, Current, Overdue, or Completed.", ex.getMessage());
    }

    @Test
    void parseRejectsSpreadsheetsMissingRequiredColumns() throws IOException {
        MockMultipartFile file = createWorkbook(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day"),
            List.of("Pump visit", "", "2026-04-14", "2026-04-14", "09:15", "11:45", "False")
        );

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_file_invalid", ex.getCode());
        assertEquals("Spreadsheet is missing required columns: Responsibility.", ex.getMessage());
    }

    @Test
    void parseReadsNumericExcelDateAndTimeCells() throws IOException {
        MockMultipartFile file = createWorkbookWithNumericDateTimeCells();

        List<ServiceCalendarSpreadsheetParser.ParsedServiceCalendarRow> rows = parser.parse(file);

        LocationEventRequest request = rows.getFirst().request();
        assertEquals(LocalDate.parse("2026-04-14"), request.date());
        assertEquals(LocalTime.parse("09:15:00"), request.time());
        assertEquals(LocalDate.parse("2026-04-15"), request.endDate());
        assertEquals(LocalTime.parse("11:45:00"), request.endTime());
    }

    @Test
    void parseRejectsSheetsWithoutHeaderRow() throws IOException {
        MockMultipartFile file = createBlankWorksheetWorkbook();

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_file_invalid", ex.getCode());
        assertEquals("Spreadsheet is missing the header row.", ex.getMessage());
    }

    @Test
    void parseRejectsWorkbooksWithoutWorksheets() throws IOException {
        MockMultipartFile file = createWorkbookWithoutWorksheets();

        ApiClientException ex = assertThrows(ApiClientException.class, () -> parser.parse(file));

        assertEquals("service_calendar_file_invalid", ex.getCode());
        assertEquals("Spreadsheet does not contain any worksheets.", ex.getMessage());
    }

    private MockMultipartFile createWorkbook(List<String> headerRow, List<String> dataRow) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Service Calendar");
            writeRow(sheet.createRow(0), headerRow);
            writeRow(sheet.createRow(1), dataRow);
            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                "service_calendar_upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createWorkbookWithNumericDateTimeCells() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Service Calendar");
            writeRow(
                sheet.createRow(0),
                List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility", "Status")
            );

            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("m/d/yyyy"));
            CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(creationHelper.createDataFormat().getFormat("h:mm"));

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Numeric pump visit");
            row.createCell(1).setCellValue("Numeric schedule");

            var startDateCell = row.createCell(2);
            startDateCell.setCellValue(DateUtil.getExcelDate(java.sql.Date.valueOf(LocalDate.parse("2026-04-14"))));
            startDateCell.setCellStyle(dateStyle);

            var endDateCell = row.createCell(3);
            endDateCell.setCellValue(DateUtil.getExcelDate(java.sql.Date.valueOf(LocalDate.parse("2026-04-15"))));
            endDateCell.setCellStyle(dateStyle);

            var startTimeCell = row.createCell(4);
            startTimeCell.setCellValue((9d * 60d + 15d) / (24d * 60d));
            startTimeCell.setCellStyle(timeStyle);

            var endTimeCell = row.createCell(5);
            endTimeCell.setCellValue((11d * 60d + 45d) / (24d * 60d));
            endTimeCell.setCellStyle(timeStyle);

            row.createCell(6).setCellValue(false);
            row.createCell(7).setCellValue("Partner");
            row.createCell(8).setCellValue("Upcoming");

            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                "service_calendar_upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createBlankWorksheetWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet("Service Calendar");
            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                "service_calendar_upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile createWorkbookWithoutWorksheets() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);

            return new MockMultipartFile(
                "file",
                "service_calendar_upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index += 1) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}
