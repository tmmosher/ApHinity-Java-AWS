package com.aphinity.client_analytics_core.api.core.services.servicecalendar;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.requests.servicecalendar.LocationEventRequest;
import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ServiceCalendarSpreadsheetParser {
    private static final List<String> REQUIRED_HEADERS = List.of(
        "Title",
        "Description",
        "Start Date",
        "End Date",
        "Start Time",
        "End Time",
        "All Day",
        "Responsibility"
    );
    private static final String OPTIONAL_STATUS_HEADER = "Status";
    private static final LocalTime ALL_DAY_START_TIME = LocalTime.MIDNIGHT;
    private static final LocalTime ALL_DAY_END_TIME = LocalTime.of(23, 59, 59);
    private static final ServiceEventStatus DEFAULT_IMPORTED_STATUS = ServiceEventStatus.UPCOMING;
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("M/d/")
            .appendValueReduced(ChronoField.YEAR, 2, 4, 2000)
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT),
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("M/d/uuuu")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
    );
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("H:mm", Locale.US).withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("H:mm:ss", Locale.US).withResolverStyle(ResolverStyle.STRICT),
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm a")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT),
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm:ss a")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
    );

    public List<ParsedServiceCalendarRow> parse(MultipartFile file) {
        requireSpreadsheet(file);
        try (InputStream inputStream = file.getInputStream();
            Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw invalidSpreadsheet("Spreadsheet does not contain any worksheets.");
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.US);
            Map<SpreadsheetColumn, Integer> columns = resolveColumns(sheet, formatter, evaluator);
            List<ParsedServiceCalendarRow> parsedRows = parseRows(sheet, columns, formatter, evaluator);
            if (parsedRows.isEmpty()) {
                throw invalidSpreadsheet("Spreadsheet does not contain any service calendar events.");
            }
            return parsedRows;
        } catch (ApiClientException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalidSpreadsheet("Spreadsheet could not be read.");
        }
    }

    private void requireSpreadsheet(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "service_calendar_file_required",
                "Service calendar spreadsheet is required."
            );
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "service_calendar_file_invalid_type",
                "Service calendar spreadsheet must be an .xlsx file."
            );
        }
    }

    private Map<SpreadsheetColumn, Integer> resolveColumns(
        Sheet sheet,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw invalidSpreadsheet("Spreadsheet is missing the header row.");
        }

        Map<SpreadsheetColumn, Integer> columns = new EnumMap<>(SpreadsheetColumn.class);
        short lastCellNumber = headerRow.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex += 1) {
            String header = normalizeCellText(headerRow.getCell(cellIndex), formatter, evaluator);
            SpreadsheetColumn column = SpreadsheetColumn.fromHeader(header);
            if (column != null) {
                columns.put(column, cellIndex);
            }
        }

        List<String> missingHeaders = REQUIRED_HEADERS.stream()
            .filter(header -> SpreadsheetColumn.fromHeader(header) != null)
            .filter(header -> !columns.containsKey(SpreadsheetColumn.fromHeader(header)))
            .toList();
        if (!missingHeaders.isEmpty()) {
            throw invalidSpreadsheet("Spreadsheet is missing required columns: " + String.join(", ", missingHeaders) + ".");
        }

        return columns;
    }

    private List<ParsedServiceCalendarRow> parseRows(
        Sheet sheet,
        Map<SpreadsheetColumn, Integer> columns,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        java.util.ArrayList<ParsedServiceCalendarRow> parsedRows = new java.util.ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowBlank(row, columns, formatter, evaluator)) {
                continue;
            }

            int spreadsheetRowNumber = rowIndex + 1;
            parsedRows.add(new ParsedServiceCalendarRow(
                spreadsheetRowNumber,
                parseRow(row, columns, formatter, evaluator, spreadsheetRowNumber)
            ));
        }
        return parsedRows;
    }

    private boolean isRowBlank(
        Row row,
        Map<SpreadsheetColumn, Integer> columns,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        for (Integer columnIndex : columns.values()) {
            if (!normalizeCellText(row.getCell(columnIndex), formatter, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private LocationEventRequest parseRow(
        Row row,
        Map<SpreadsheetColumn, Integer> columns,
        DataFormatter formatter,
        FormulaEvaluator evaluator,
        int rowNumber
    ) {
        String title = normalizeCellText(row.getCell(columns.get(SpreadsheetColumn.TITLE)), formatter, evaluator);
        String description = normalizeCellText(row.getCell(columns.get(SpreadsheetColumn.DESCRIPTION)), formatter, evaluator);
        LocalDate startDate = parseDateCell(row.getCell(columns.get(SpreadsheetColumn.START_DATE)), rowNumber, "Start Date", evaluator);
        LocalDate endDate = parseDateCell(row.getCell(columns.get(SpreadsheetColumn.END_DATE)), rowNumber, "End Date", evaluator);
        boolean allDay = parseAllDayCell(row.getCell(columns.get(SpreadsheetColumn.ALL_DAY)), rowNumber, formatter, evaluator);
        ServiceEventResponsibility responsibility = parseResponsibility(
            row.getCell(columns.get(SpreadsheetColumn.RESPONSIBILITY)),
            rowNumber,
            formatter,
            evaluator
        );
        ServiceEventStatus status = parseStatus(
            columns.containsKey(SpreadsheetColumn.STATUS) ? row.getCell(columns.get(SpreadsheetColumn.STATUS)) : null,
            rowNumber,
            formatter,
            evaluator
        );

        if (title.isBlank()) {
            throw rowInvalid(rowNumber, "Title is required.");
        }
        if (title.length() > ServiceEvent.TITLE_MAX_LENGTH) {
            throw rowInvalid(rowNumber, "Title must be 42 characters or fewer.");
        }

        if (startDate == null) {
            throw rowInvalid(rowNumber, "Start Date is required.");
        }

        if (allDay) {
            validateDateRange(rowNumber, startDate, ALL_DAY_START_TIME, endDate == null ? startDate : endDate, ALL_DAY_END_TIME);
            return new LocationEventRequest(
                title,
                responsibility,
                startDate,
                ALL_DAY_START_TIME,
                endDate == null ? startDate : endDate,
                ALL_DAY_END_TIME,
                description.isBlank() ? null : description,
                status
            );
        }

        LocalTime startTime = parseTimeCell(row.getCell(columns.get(SpreadsheetColumn.START_TIME)), rowNumber, "Start Time", evaluator);
        LocalTime endTime = parseTimeCell(row.getCell(columns.get(SpreadsheetColumn.END_TIME)), rowNumber, "End Time", evaluator);

        if (startTime == null) {
            throw rowInvalid(rowNumber, "Start Time is required.");
        }

        validateDateRange(
            rowNumber,
            startDate,
            startTime,
            endDate == null ? startDate : endDate,
            endTime == null ? startTime : endTime
        );

        return new LocationEventRequest(
            title,
            responsibility,
            startDate,
            startTime,
            endDate == null ? startDate : endDate,
            endTime == null ? startTime : endTime,
            description.isBlank() ? null : description,
            status
        );
    }

    private void validateDateRange(
        int rowNumber,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime
    ) {
        LocalDateTime startDateTime = LocalDateTime.of(startDate, startTime);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, endTime);
        if (endDateTime.isBefore(startDateTime)) {
            throw rowInvalid(rowNumber, "End date and time must be on or after the start date and time.");
        }
    }

    private LocalDate parseDateCell(Cell cell, int rowNumber, String label, FormulaEvaluator evaluator) {
        if (isBlankCell(cell, evaluator)) {
            return null;
        }
        CellType type = resolveCellType(cell, evaluator);
        try {
            if (type == CellType.NUMERIC) {
                double numericValue = cell.getNumericCellValue();
                if (numericValue < 1d) {
                    throw rowInvalid(rowNumber, label + " is invalid.");
                }
                LocalDateTime dateTime = DateUtil.getLocalDateTime(numericValue);
                return dateTime.toLocalDate();
            }
            String normalized = cell.getStringCellValue().strip();
            if (normalized.isBlank()) {
                return null;
            }
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(normalized, formatter);
                } catch (DateTimeParseException ignored) {
                    // Try the next format.
                }
            }
        } catch (DateTimeException | IllegalStateException ex) {
            throw rowInvalid(rowNumber, label + " is invalid.");
        }

        throw rowInvalid(rowNumber, label + " is invalid.");
    }

    private LocalTime parseTimeCell(Cell cell, int rowNumber, String label, FormulaEvaluator evaluator) {
        if (isBlankCell(cell, evaluator)) {
            return null;
        }
        CellType type = resolveCellType(cell, evaluator);
        try {
            if (type == CellType.NUMERIC) {
                double numericValue = cell.getNumericCellValue();
                double dayFraction = numericValue % 1;
                if (dayFraction < 0) {
                    dayFraction += 1;
                }
                LocalDateTime dateTime = DateUtil.getLocalDateTime(dayFraction);
                return dateTime.toLocalTime();
            }
            String normalized = cell.getStringCellValue().strip();
            if (normalized.isBlank()) {
                return null;
            }
            for (DateTimeFormatter formatter : TIME_FORMATTERS) {
                try {
                    return LocalTime.parse(normalized, formatter);
                } catch (DateTimeParseException ignored) {
                    // Try the next format.
                }
            }
        } catch (DateTimeException | IllegalStateException ex) {
            throw rowInvalid(rowNumber, label + " is invalid.");
        }

        throw rowInvalid(rowNumber, label + " is invalid.");
    }

    private boolean parseAllDayCell(Cell cell, int rowNumber, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (isBlankCell(cell, evaluator)) {
            return false;
        }
        CellType type = resolveCellType(cell, evaluator);
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        if (type == CellType.NUMERIC) {
            double numericValue = cell.getNumericCellValue();
            if (numericValue == 1d) {
                return true;
            }
            if (numericValue == 0d) {
                return false;
            }
        }

        String normalized = normalizeCellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        throw rowInvalid(rowNumber, "All Day must be True or False.");
    }

    private ServiceEventResponsibility parseResponsibility(
        Cell cell,
        int rowNumber,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String normalized = normalizeCellText(cell, formatter, evaluator);
        ServiceEventResponsibility responsibility;
        try {
            responsibility = ServiceEventResponsibility.fromValue(normalized);
        } catch (IllegalArgumentException ex) {
            throw rowInvalid(rowNumber, "Responsibility must be Client or Partner.");
        }

        if (responsibility == null) {
            throw rowInvalid(rowNumber, "Responsibility must be Client or Partner.");
        }
        return responsibility;
    }

    private ServiceEventStatus parseStatus(
        Cell cell,
        int rowNumber,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        String normalized = normalizeCellText(cell, formatter, evaluator);
        if (normalized.isBlank()) {
            return DEFAULT_IMPORTED_STATUS;
        }

        try {
            ServiceEventStatus status = ServiceEventStatus.fromValue(normalized);
            if (status != null) {
                return status;
            }
        } catch (IllegalArgumentException ex) {
            throw rowInvalid(rowNumber, "Status must be Upcoming, Current, Overdue, or Completed.");
        }

        throw rowInvalid(rowNumber, "Status must be Upcoming, Current, Overdue, or Completed.");
    }

    private String normalizeCellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).strip();
    }

    private boolean isBlankCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return true;
        }
        CellType type = resolveCellType(cell, evaluator);
        return type == CellType.BLANK || (type == CellType.STRING && cell.getStringCellValue().strip().isBlank());
    }

    private CellType resolveCellType(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return CellType.BLANK;
        }
        return cell.getCellType() == CellType.FORMULA ? evaluator.evaluateFormulaCell(cell) : cell.getCellType();
    }

    private ApiClientException invalidSpreadsheet(String message) {
        return new ApiClientException(HttpStatus.BAD_REQUEST, "service_calendar_file_invalid", message);
    }

    private ApiClientException rowInvalid(int rowNumber, String message) {
        return new ApiClientException(
            HttpStatus.BAD_REQUEST,
            "service_calendar_row_invalid",
            "Row " + rowNumber + ": " + message
        );
    }

    public record ParsedServiceCalendarRow(int rowNumber, LocationEventRequest request) {
    }

    private enum SpreadsheetColumn {
        TITLE("Title"),
        DESCRIPTION("Description"),
        START_DATE("Start Date"),
        END_DATE("End Date"),
        START_TIME("Start Time"),
        END_TIME("End Time"),
        ALL_DAY("All Day"),
        RESPONSIBILITY("Responsibility"),
        STATUS(OPTIONAL_STATUS_HEADER);

        private final String header;

        SpreadsheetColumn(String header) {
            this.header = header;
        }

        private static SpreadsheetColumn fromHeader(String header) {
            if (header == null || header.isBlank()) {
                return null;
            }
            for (SpreadsheetColumn value : values()) {
                if (value.header.equalsIgnoreCase(header.strip())) {
                    return value;
                }
            }
            return null;
        }
    }
}
