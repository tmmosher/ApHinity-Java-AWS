package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.error.ApiClientException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the dashboard workbook template into a row-oriented representation that keeps
 * cell ordering intact for stateful sublocation/system collation.
 */
@Service
public class LocationDashboardSpreadsheetParser {
    private static final int MIN_DATE_ROW_SCORE = 1;
    private static final String HEADER_FACILITY = "facility";
    private static final String HEADER_BUILDING = "bldg (collated if unrecognized)";
    private static final String HEADER_SYSTEM = "system (collated if unrecognized)";
    private static final String HEADER_POINT_OF_USE = "point of use (ignored)";
    private static final String HEADER_BASIS = "basis (ignored)";
    // Accept both the verbose template labels and the shorter workbook labels.
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
        Map.entry("facility", HEADER_FACILITY),
        Map.entry("bldg", HEADER_BUILDING),
        Map.entry("building", HEADER_BUILDING),
        Map.entry(HEADER_BUILDING, HEADER_BUILDING),
        Map.entry("system", HEADER_SYSTEM),
        Map.entry(HEADER_SYSTEM, HEADER_SYSTEM),
        Map.entry("point of use", HEADER_POINT_OF_USE),
        Map.entry(HEADER_POINT_OF_USE, HEADER_POINT_OF_USE),
        Map.entry("basis", HEADER_BASIS),
        Map.entry(HEADER_BASIS, HEADER_BASIS)
    );
    private static final Pattern NUMERIC_TEXT_PATTERN = Pattern.compile(
        "^[<>]=?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$|^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$"
    );
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

    public ParsedDashboardWorkbook parse(MultipartFile file) {
        requireSpreadsheet(file);
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw invalidSpreadsheet("Spreadsheet does not contain any worksheets.");
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.US);
            WorksheetLayout layout = resolveWorksheetLayout(sheet, formatter, evaluator);
            List<MetricColumn> metricColumns = resolveMetricColumns(sheet, layout, formatter, evaluator);
            List<ParsedDashboardRow> rows = parseRows(sheet, layout, metricColumns, formatter, evaluator);
            if (rows.isEmpty()) {
                throw invalidSpreadsheet("Spreadsheet does not contain any dashboard data rows.");
            }
            return new ParsedDashboardWorkbook(layout.locationTitle(), rows);
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
                "location_dashboard_file_required",
                "Dashboard spreadsheet is required."
            );
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_file_invalid_type",
                "Dashboard spreadsheet must be an .xlsx file."
            );
        }
    }

    private WorksheetLayout resolveWorksheetLayout(
        Sheet sheet,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        HeaderMatch bestHeaderMatch = null;
        // Some generated workbooks shift the header block downward, so scan the whole sheet
        // instead of assuming the header lives near the top.
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> headers = resolveHeaderColumns(row, formatter, evaluator);
            if (!containsRequiredHeaders(headers)) {
                continue;
            }

            int headerScore = scoreHeaderColumns(headers);
            if (bestHeaderMatch == null
                || headerScore > bestHeaderMatch.score()
                || (headerScore == bestHeaderMatch.score() && row.getRowNum() < bestHeaderMatch.rowIndex())) {
                bestHeaderMatch = new HeaderMatch(row.getRowNum(), headers, headerScore);
            }
        }

        if (bestHeaderMatch != null) {
            int headerRowIndex = bestHeaderMatch.rowIndex();
            Map<String, Integer> headers = bestHeaderMatch.headers();
            Row headerRow = sheet.getRow(headerRowIndex);
            int basisColumnIndex = headers.getOrDefault(HEADER_BASIS, 4);
            int titleRowIndex = resolveTitleRowIndex(sheet, headerRowIndex, basisColumnIndex, formatter, evaluator);
            int dateRowIndex = resolveDateRowIndex(sheet, headerRowIndex, basisColumnIndex, formatter, evaluator);
            int metricHeaderRowIndex = resolveMetricRowIndex(sheet, dateRowIndex, basisColumnIndex, formatter, evaluator);
            String locationTitle = firstNonBlankCellText(sheet.getRow(titleRowIndex), formatter, evaluator);

            return new WorksheetLayout(
                locationTitle == null ? null : locationTitle.strip(),
                headerRowIndex,
                metricHeaderRowIndex,
                dateRowIndex,
                headers.getOrDefault(HEADER_FACILITY, 0),
                headers.getOrDefault(HEADER_BUILDING, 1),
                headers.getOrDefault(HEADER_SYSTEM, 2),
                headers.getOrDefault(HEADER_POINT_OF_USE, 3),
                basisColumnIndex,
                headerRow == null ? 0 : headerRow.getLastCellNum()
            );
        }
        throw invalidSpreadsheet("Spreadsheet is missing the dashboard header row.");
    }

    private int resolveTitleRowIndex(
        Sheet sheet,
        int headerRowIndex,
        int basisColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // Different spreadsheet generators insert a little extra vertical spacing.
        // Treat the title as the contiguous label block immediately above the header.
        int rowIndex = headerRowIndex - 1;
        while (rowIndex >= 0 && !hasLeadingLabelText(sheet.getRow(rowIndex), basisColumnIndex, formatter, evaluator)) {
            rowIndex -= 1;
        }
        if (rowIndex < 0) {
            throw invalidSpreadsheet("Spreadsheet is missing the location title.");
        }
        while (rowIndex - 1 >= 0 && hasLeadingLabelText(sheet.getRow(rowIndex - 1), basisColumnIndex, formatter, evaluator)) {
            rowIndex -= 1;
        }
        return rowIndex;
    }

    private int resolveDateRowIndex(
        Sheet sheet,
        int headerRowIndex,
        int basisColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // Date rows are the strongest signal in the worksheet because they repeat across the metric columns.
        int bestRowIndex = -1;
        int bestScore = 0;
        for (int rowIndex = 0; rowIndex < headerRowIndex; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            int score = scoreDateRow(row, basisColumnIndex, formatter, evaluator);
            if (score > bestScore) {
                bestScore = score;
                bestRowIndex = rowIndex;
            }
        }
        if (bestRowIndex < 0 || bestScore < MIN_DATE_ROW_SCORE) {
            throw invalidSpreadsheet("Spreadsheet is missing the date row.");
        }
        return bestRowIndex;
    }

    private int resolveMetricRowIndex(
        Sheet sheet,
        int dateRowIndex,
        int basisColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // The metric header is the topmost data row above the date row that still contains metric text/merges.
        int bestRowIndex = -1;
        int bestScore = 0;
        for (int rowIndex = 0; rowIndex < dateRowIndex; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            int score = scoreMetricRow(sheet, row, basisColumnIndex, formatter, evaluator);
            if (score > bestScore) {
                bestScore = score;
                bestRowIndex = rowIndex;
            }
        }
        if (bestRowIndex < 0 || bestScore == 0) {
            throw invalidSpreadsheet("Spreadsheet does not contain any metric columns.");
        }
        return bestRowIndex;
    }

    private int scoreDateRow(Row row, int basisColumnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = basisColumnIndex + 1; cellIndex < lastCellNumber; cellIndex += 1) {
            if (isDateLikeCell(row.getCell(cellIndex), formatter, evaluator)) {
                score += 1;
            }
        }
        return score;
    }

    private int scoreMetricRow(
        Sheet sheet,
        Row row,
        int basisColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        if (row == null) {
            return 0;
        }

        int score = 0;
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = basisColumnIndex + 1; cellIndex < lastCellNumber; cellIndex += 1) {
            if (!normalizeCellText(row.getCell(cellIndex), formatter, evaluator).isBlank()) {
                score += 1;
            }
        }

        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.getFirstRow() != row.getRowNum()
                || mergedRegion.getLastRow() != row.getRowNum()
                || mergedRegion.getFirstColumn() <= basisColumnIndex) {
                continue;
            }
            String metricName = normalizeCellText(row.getCell(mergedRegion.getFirstColumn()), formatter, evaluator);
            if (!metricName.isBlank()) {
                score += 2;
            }
        }
        return score;
    }

    private boolean hasLeadingLabelText(
        Row row,
        int basisColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        if (row == null) {
            return false;
        }
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex <= basisColumnIndex && cellIndex < lastCellNumber; cellIndex += 1) {
            if (!normalizeCellText(row.getCell(cellIndex), formatter, evaluator).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isDateLikeCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        String normalizedText = blankToNull(normalizeCellText(cell, formatter, evaluator));
        if (normalizedText == null) {
            return false;
        }
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(normalizedText, dateFormatter);
                return true;
            } catch (DateTimeParseException ignored) {
            }
        }
        return false;
    }

    private Map<String, Integer> resolveHeaderColumns(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex += 1) {
            String normalizedHeader = normalizeHeader(normalizeCellText(row.getCell(cellIndex), formatter, evaluator));
            String canonicalHeader = resolveCanonicalHeader(normalizedHeader);
            if (canonicalHeader != null) {
                columns.put(canonicalHeader, cellIndex);
            }
        }
        return columns;
    }

    private boolean containsRequiredHeaders(Map<String, Integer> headers) {
        return headers.containsKey(HEADER_FACILITY)
            && headers.containsKey(HEADER_BUILDING)
            && headers.containsKey(HEADER_SYSTEM);
    }

    private int scoreHeaderColumns(Map<String, Integer> headers) {
        int score = 0;
        if (headers.containsKey(HEADER_FACILITY)) {
            score += 3;
        }
        if (headers.containsKey(HEADER_BUILDING)) {
            score += 3;
        }
        if (headers.containsKey(HEADER_SYSTEM)) {
            score += 3;
        }
        if (headers.containsKey(HEADER_POINT_OF_USE)) {
            score += 1;
        }
        if (headers.containsKey(HEADER_BASIS)) {
            score += 1;
        }
        return score;
    }

    private List<MetricColumn> resolveMetricColumns(
        Sheet sheet,
        WorksheetLayout layout,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        List<MetricColumn> metricColumns = new ArrayList<>();
        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.getFirstRow() != layout.metricHeaderRowIndex()
                || mergedRegion.getLastRow() != layout.metricHeaderRowIndex()
                || mergedRegion.getFirstColumn() <= layout.basisColumnIndex()) {
                continue;
            }

            Row metricRow = sheet.getRow(layout.metricHeaderRowIndex());
            String metricName = normalizeCellText(metricRow == null ? null : metricRow.getCell(mergedRegion.getFirstColumn()), formatter, evaluator);
            if (metricName.isBlank()) {
                continue;
            }
            for (int columnIndex = mergedRegion.getFirstColumn(); columnIndex <= mergedRegion.getLastColumn(); columnIndex += 1) {
                metricColumns.add(new MetricColumn(
                    metricName.strip(),
                    columnIndex,
                    parseDateCell(
                        rowCell(sheet, layout.dateRowIndex(), columnIndex),
                        layout.dateRowIndex() + 1,
                        evaluator
                    )
                ));
            }
        }

        if (!metricColumns.isEmpty()) {
            return metricColumns.stream()
                .sorted(java.util.Comparator.comparingInt(MetricColumn::columnIndex))
                .toList();
        }

        Row metricRow = sheet.getRow(layout.metricHeaderRowIndex());
        if (metricRow == null) {
            throw invalidSpreadsheet("Spreadsheet is missing the metric header row.");
        }
        String currentMetricName = null;
        for (int columnIndex = layout.basisColumnIndex() + 1; columnIndex < Math.max(metricRow.getLastCellNum(), layout.lastCellNumber()); columnIndex += 1) {
            String metricName = normalizeCellText(metricRow.getCell(columnIndex), formatter, evaluator);
            if (!metricName.isBlank()) {
                currentMetricName = metricName.strip();
            }
            if (currentMetricName == null) {
                continue;
            }
            metricColumns.add(new MetricColumn(
                currentMetricName,
                columnIndex,
                parseDateCell(
                    rowCell(sheet, layout.dateRowIndex(), columnIndex),
                    layout.dateRowIndex() + 1,
                    evaluator
                )
            ));
        }

        if (metricColumns.isEmpty()) {
            throw invalidSpreadsheet("Spreadsheet does not contain any metric columns.");
        }
        return metricColumns.stream()
            .sorted(java.util.Comparator.comparingInt(MetricColumn::columnIndex))
            .toList();
    }

    private List<ParsedDashboardRow> parseRows(
        Sheet sheet,
        WorksheetLayout layout,
        List<MetricColumn> metricColumns,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        List<ParsedDashboardRow> rows = new ArrayList<>();
        for (int rowIndex = layout.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String facility = blankToNull(normalizeCellText(row.getCell(layout.facilityColumnIndex()), formatter, evaluator));
            String building = blankToNull(normalizeCellText(row.getCell(layout.buildingColumnIndex()), formatter, evaluator));
            String system = blankToNull(normalizeCellText(row.getCell(layout.systemColumnIndex()), formatter, evaluator));
            String pointOfUse = blankToNull(normalizeCellText(row.getCell(layout.pointOfUseColumnIndex()), formatter, evaluator));
            String basis = blankToNull(normalizeCellText(row.getCell(layout.basisColumnIndex()), formatter, evaluator));

            List<ParsedDashboardCell> cells = new ArrayList<>();
            for (MetricColumn metricColumn : metricColumns) {
                Cell cell = row.getCell(metricColumn.columnIndex());
                String rawValue = blankToNull(normalizeCellText(cell, formatter, evaluator));
                String commentText = parseComment(cell);
                if (rawValue == null && commentText == null) {
                    continue;
                }
                if (rawValue != null && isIgnoredSemanticMeasurementValue(rawValue) && commentText == null) {
                    continue;
                }
                BigDecimal numericValue = parseMeasurementValue(cell, rawValue, evaluator);
                cells.add(new ParsedDashboardCell(
                    metricColumn.metricName(),
                    metricColumn.observedDate(),
                    rawValue,
                    numericValue,
                    commentText,
                    cell == null ? null : cell.getAddress().formatAsString()
                ));
            }

            if (facility == null && building == null && system == null && pointOfUse == null && basis == null && cells.isEmpty()) {
                continue;
            }

            rows.add(new ParsedDashboardRow(
                rowIndex + 1,
                facility,
                building,
                system,
                pointOfUse,
                basis,
                List.copyOf(cells)
            ));
        }
        return List.copyOf(rows);
    }

    private LocalDate parseDateCell(Cell cell, int rowNumber, FormulaEvaluator evaluator) {
        if (cell == null) {
            throw invalidSpreadsheet("Row " + rowNumber + ": Date column is blank.");
        }

        CellType effectiveType = cell.getCellType() == CellType.FORMULA
            ? evaluator.evaluateFormulaCell(cell)
            : cell.getCellType();
        if (effectiveType == CellType.NUMERIC) {
            CellValue evaluatedCell = cell.getCellType() == CellType.FORMULA ? evaluator.evaluate(cell) : null;
            double numericDate = evaluatedCell == null ? cell.getNumericCellValue() : evaluatedCell.getNumberValue();
            return DateUtil.getLocalDateTime(numericDate).toLocalDate();
        }

        String rawValue = cell.getCellType() == CellType.FORMULA
            ? evaluator.evaluate(cell).formatAsString()
            : cell.toString();
        String normalizedText = blankToNull(rawValue == null ? null : rawValue.strip());
        if (normalizedText == null) {
            throw invalidSpreadsheet("Row " + rowNumber + ": Date column is blank.");
        }
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalizedText, dateFormatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw invalidSpreadsheet("Row " + rowNumber + ": Date column is invalid.");
    }

    private BigDecimal parseMeasurementValue(Cell cell, String rawValue, FormulaEvaluator evaluator) {
        if (cell == null || rawValue == null) {
            return null;
        }
        CellType effectiveType = cell.getCellType() == CellType.FORMULA
            ? evaluator.evaluateFormulaCell(cell)
            : cell.getCellType();
        if (effectiveType == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            CellValue evaluatedCell = cell.getCellType() == CellType.FORMULA ? evaluator.evaluate(cell) : null;
            double numericValue = evaluatedCell == null ? cell.getNumericCellValue() : evaluatedCell.getNumberValue();
            return BigDecimal.valueOf(numericValue);
        }

        String cleanedValue = rawValue.replace(",", "").strip();
        if (cleanedValue.equalsIgnoreCase("nd")
            || cleanedValue.equalsIgnoreCase("n.d.")
            || cleanedValue.equalsIgnoreCase("not detected")) {
            return BigDecimal.ZERO;
        }
        if (isIgnoredSemanticMeasurementValue(cleanedValue)) {
            return null;
        }
        Matcher numericTextMatcher = NUMERIC_TEXT_PATTERN.matcher(cleanedValue);
        if (!numericTextMatcher.matches()) {
            return null;
        }
        String numericPortion = numericTextMatcher.group(1) != null
            ? numericTextMatcher.group(1)
            : numericTextMatcher.group(2);
        if (numericPortion == null) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumericPortion(numericPortion));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeNumericPortion(String numericPortion) {
        if (numericPortion.startsWith("+.")) {
            return "+0" + numericPortion.substring(1);
        }
        if (numericPortion.startsWith("-.")) {
            return "-0" + numericPortion.substring(1);
        }
        if (numericPortion.startsWith(".")) {
            return "0" + numericPortion;
        }
        return numericPortion;
    }

    private boolean isIgnoredSemanticMeasurementValue(String value) {
        if (value == null) {
            return false;
        }
        String cleanedValue = value.replace(",", "").strip();
        return cleanedValue.equalsIgnoreCase("nt")
            || cleanedValue.equalsIgnoreCase("n.t.")
            || cleanedValue.equalsIgnoreCase("not tested");
    }

    private String parseComment(Cell cell) {
        if (cell == null || cell.getCellComment() == null || cell.getCellComment().getString() == null) {
            return null;
        }
        String rawComment = cell.getCellComment().getString().getString();
        if (rawComment == null) {
            return null;
        }
        String normalizedComment = rawComment.strip();
        return normalizedComment.isBlank() ? null : normalizedComment;
    }

    private Cell rowCell(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(columnIndex);
    }

    private String firstNonBlankCellText(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return null;
        }
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex += 1) {
            String text = normalizeCellText(row.getCell(cellIndex), formatter, evaluator);
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String normalizeCellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell, evaluator);
        return value == null ? "" : value.strip();
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String resolveCanonicalHeader(String normalizedHeader) {
        if (normalizedHeader == null || normalizedHeader.isBlank()) {
            return null;
        }
        return HEADER_ALIASES.get(normalizedHeader);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private ApiClientException invalidSpreadsheet(String message) {
        return new ApiClientException(
            HttpStatus.BAD_REQUEST,
            "location_dashboard_file_invalid",
            message
        );
    }

    private record WorksheetLayout(
        String locationTitle,
        int headerRowIndex,
        int metricHeaderRowIndex,
        int dateRowIndex,
        int facilityColumnIndex,
        int buildingColumnIndex,
        int systemColumnIndex,
        int pointOfUseColumnIndex,
        int basisColumnIndex,
        int lastCellNumber
    ) {
    }

    private record HeaderMatch(
        int rowIndex,
        Map<String, Integer> headers,
        int score
    ) {
    }

    private record MetricColumn(
        String metricName,
        int columnIndex,
        LocalDate observedDate
    ) {
    }

    public record ParsedDashboardWorkbook(
        String locationTitle,
        List<ParsedDashboardRow> rows
    ) {
    }

    public record ParsedDashboardRow(
        int rowNumber,
        String facility,
        String building,
        String system,
        String pointOfUse,
        String basis,
        List<ParsedDashboardCell> cells
    ) {
    }

    public record ParsedDashboardCell(
        String metricName,
        LocalDate observedDate,
        String rawValue,
        BigDecimal numericValue,
        String commentText,
        String cellReference
    ) {
    }
}
