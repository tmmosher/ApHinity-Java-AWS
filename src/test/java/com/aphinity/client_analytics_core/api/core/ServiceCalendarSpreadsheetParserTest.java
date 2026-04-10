package com.aphinity.client_analytics_core.api.core;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.core.services.servicecalendar.ServiceCalendarSpreadsheetParser;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index += 1) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}
