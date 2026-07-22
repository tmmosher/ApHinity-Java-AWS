package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.error.ApiClientException;
import com.aphinity.client_analytics_core.api.core.services.SpreadsheetFileTypes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a configured dashboard workbook into rows with ordered, dynamic identity values.
 */
@Service
public class LocationDashboardSpreadsheetParser implements DashboardWorkbookParser {
    private static final int MIN_DATE_ROW_SCORE = 1;
    private static final String VALIDATION_SHEET_NAME = "validation";
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

    /**
     * Parses a dashboard spreadsheet using a strategy-specific identity column
     * pattern.
     *
     * @param file uploaded .xlsx or .xlsm workbook; VBA projects are never accessed or executed
     * @param identityPattern configured identity headers and aliases
     * @return parsed workbook rows and cells
     */
    @Override
    public ParsedDashboardWorkbook parse(
        MultipartFile file,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        requireSpreadsheet(file);
        HeaderIdentityPattern headerPattern = buildHeaderIdentityPattern(identityPattern);
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveValidationSheet(workbook);
            if (sheet == null) {
                throw invalidSpreadsheet("Spreadsheet must contain a 'Validation' worksheet.");
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.US);
            WorksheetLayout layout = resolveWorksheetLayout(sheet, formatter, evaluator, headerPattern);
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

    private HeaderIdentityPattern buildHeaderIdentityPattern(
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    ) {
        Map<String, String> headerAliases = new LinkedHashMap<>();
        Set<String> requiredHeaders = new LinkedHashSet<>();
        Set<String> normalizedIdentityKeys = new LinkedHashSet<>();
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> effectiveIdentityPattern =
            identityPattern == null ? List.of() : identityPattern;

        if (effectiveIdentityPattern.isEmpty()) {
            throw new IllegalStateException("Dashboard spreadsheet identity columns are required.");
        }

        for (LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn identityColumn : effectiveIdentityPattern) {
            if (identityColumn == null) {
                continue;
            }
            String identityKey = identityColumn.identityKey();
            String normalizedColumn = normalizeHeader(identityColumn.column());
            if (identityKey == null || identityKey.isBlank() || normalizedColumn.isBlank()) {
                throw new IllegalStateException("Dashboard spreadsheet identity column header is required.");
            }
            if (!normalizedIdentityKeys.add(normalizedColumn) || !requiredHeaders.add(identityKey)) {
                throw new IllegalStateException(
                    "Dashboard spreadsheet identity columns must be unique: " + identityKey
                );
            }
            registerHeaderAlias(headerAliases, normalizedColumn, identityKey);
            for (String rawAlias : identityColumn.aliases()) {
                String normalizedAlias = normalizeHeader(rawAlias);
                if (!normalizedAlias.isBlank()) {
                    registerHeaderAlias(headerAliases, normalizedAlias, identityKey);
                }
            }
        }
        return new HeaderIdentityPattern(
            Collections.unmodifiableMap(new LinkedHashMap<>(headerAliases)),
            Collections.unmodifiableSet(new LinkedHashSet<>(requiredHeaders))
        );
    }

    private void registerHeaderAlias(
        Map<String, String> headerAliases,
        String normalizedHeader,
        String identityKey
    ) {
        String existingIdentityKey = headerAliases.putIfAbsent(normalizedHeader, identityKey);
        if (existingIdentityKey != null && !existingIdentityKey.equals(identityKey)) {
            throw new IllegalStateException(
                "Dashboard spreadsheet identity header aliases must be unique: " + normalizedHeader
            );
        }
    }

    private Sheet resolveValidationSheet(Workbook workbook) {
        if (workbook == null || workbook.getNumberOfSheets() == 0) {
            return null;
        }
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex += 1) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            if (sheet != null && VALIDATION_SHEET_NAME.equals(normalizeKey(sheet.getSheetName()))) {
                return sheet;
            }
        }
        return null;
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
        if (!SpreadsheetFileTypes.isSupportedOfficeOpenXmlSpreadsheet(fileName)) {
            throw new ApiClientException(
                HttpStatus.BAD_REQUEST,
                "location_dashboard_file_invalid_type",
                "Dashboard spreadsheet must be an .xlsx or .xlsm file."
            );
        }
    }

    private WorksheetLayout resolveWorksheetLayout(
        Sheet sheet,
        DataFormatter formatter,
        FormulaEvaluator evaluator,
        HeaderIdentityPattern headerPattern
    ) {
        HeaderMatch bestHeaderMatch = null;
        // Some generated workbooks shift the header block downward, so scan the whole sheet
        // instead of assuming the header lives near the top.
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> headers = resolveHeaderColumns(row, formatter, evaluator, headerPattern.headerAliases());
            if (!containsRequiredHeaders(headers, headerPattern.requiredHeaders())) {
                continue;
            }

            int headerScore = scoreHeaderColumns(headers, headerPattern.requiredHeaders());
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
            int identityEndColumnIndex = resolveIdentityEndColumnIndex(headers);
            int titleRowIndex = resolveTitleRowIndex(sheet, headerRowIndex, identityEndColumnIndex, formatter, evaluator);
            int dateRowIndex = resolveDateRowIndex(sheet, headerRowIndex, identityEndColumnIndex, formatter, evaluator);
            int metricHeaderRowIndex = resolveMetricRowIndex(sheet, dateRowIndex, identityEndColumnIndex, formatter, evaluator);
            String locationTitle = firstNonBlankCellText(sheet.getRow(titleRowIndex), formatter, evaluator);
            Map<String, Integer> orderedIdentityColumns = new LinkedHashMap<>();
            for (String identityKey : headerPattern.requiredHeaders()) {
                orderedIdentityColumns.put(identityKey, headers.get(identityKey));
            }

            return new WorksheetLayout(
                locationTitle == null ? null : locationTitle.strip(),
                headerRowIndex,
                metricHeaderRowIndex,
                dateRowIndex,
                Collections.unmodifiableMap(orderedIdentityColumns),
                identityEndColumnIndex,
                headerRow == null ? 0 : headerRow.getLastCellNum()
            );
        }
        throw invalidSpreadsheet("Spreadsheet is missing the dashboard header row.");
    }

    private int resolveTitleRowIndex(
        Sheet sheet,
        int headerRowIndex,
        int identityEndColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // Different spreadsheet generators insert a little extra vertical spacing.
        // Treat the title as the contiguous label block immediately above the header.
        int rowIndex = headerRowIndex - 1;
        while (rowIndex >= 0 && !hasLeadingLabelText(sheet.getRow(rowIndex), identityEndColumnIndex, formatter, evaluator)) {
            rowIndex -= 1;
        }
        if (rowIndex < 0) {
            throw invalidSpreadsheet("Spreadsheet is missing the location title.");
        }
        while (rowIndex - 1 >= 0 && hasLeadingLabelText(sheet.getRow(rowIndex - 1), identityEndColumnIndex, formatter, evaluator)) {
            rowIndex -= 1;
        }
        return rowIndex;
    }

    private int resolveDateRowIndex(
        Sheet sheet,
        int headerRowIndex,
        int identityEndColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // Date rows are the strongest signal in the worksheet because they repeat across the metric columns.
        // The compact Apple destination layout places identity labels and dates on
        // the same row, so check the identity header row before scanning above it.
        if (scoreDateRow(sheet.getRow(headerRowIndex), identityEndColumnIndex, formatter, evaluator) >= MIN_DATE_ROW_SCORE) {
            return headerRowIndex;
        }

        int bestRowIndex = -1;
        int bestScore = 0;
        for (int rowIndex = 0; rowIndex < headerRowIndex; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            int score = scoreDateRow(row, identityEndColumnIndex, formatter, evaluator);
            if (score > bestScore) {
                bestScore = score;
                bestRowIndex = rowIndex;
            }
        }
        if (bestRowIndex < 0) {
            throw invalidSpreadsheet("Spreadsheet is missing the date row.");
        }
        return bestRowIndex;
    }

    private int resolveMetricRowIndex(
        Sheet sheet,
        int dateRowIndex,
        int identityEndColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        // The metric header is the topmost data row above the date row that still contains metric text/merges.
        int bestRowIndex = -1;
        int bestScore = 0;
        for (int rowIndex = 0; rowIndex < dateRowIndex; rowIndex += 1) {
            Row row = sheet.getRow(rowIndex);
            int score = scoreMetricRow(sheet, row, identityEndColumnIndex, formatter, evaluator);
            if (score > bestScore) {
                bestScore = score;
                bestRowIndex = rowIndex;
            }
        }
        if (bestRowIndex < 0) {
            throw invalidSpreadsheet("Spreadsheet does not contain any metric columns.");
        }
        return bestRowIndex;
    }

    private int scoreDateRow(Row row, int identityEndColumnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = identityEndColumnIndex + 1; cellIndex < lastCellNumber; cellIndex += 1) {
            if (isDateLikeCell(row.getCell(cellIndex), formatter, evaluator)) {
                score += 1;
            }
        }
        return score;
    }

    private int scoreMetricRow(
        Sheet sheet,
        Row row,
        int identityEndColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        if (row == null) {
            return 0;
        }

        int score = 0;
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = identityEndColumnIndex + 1; cellIndex < lastCellNumber; cellIndex += 1) {
            if (!normalizeCellText(row.getCell(cellIndex), formatter, evaluator).isBlank()) {
                score += 1;
            }
        }

        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.getFirstRow() != row.getRowNum()
                || mergedRegion.getLastRow() != row.getRowNum()
                || mergedRegion.getFirstColumn() <= identityEndColumnIndex) {
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
        int identityEndColumnIndex,
        DataFormatter formatter,
        FormulaEvaluator evaluator
    ) {
        if (row == null) {
            return false;
        }
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex <= identityEndColumnIndex && cellIndex < lastCellNumber; cellIndex += 1) {
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

    private Map<String, Integer> resolveHeaderColumns(
        Row row,
        DataFormatter formatter,
        FormulaEvaluator evaluator,
        Map<String, String> headerAliases
    ) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        short lastCellNumber = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex += 1) {
            String normalizedHeader = normalizeHeader(normalizeCellText(row.getCell(cellIndex), formatter, evaluator));
            String canonicalHeader = resolveCanonicalHeader(normalizedHeader, headerAliases);
            if (canonicalHeader != null) {
                columns.put(canonicalHeader, cellIndex);
            }
        }
        return columns;
    }

    private boolean containsRequiredHeaders(Map<String, Integer> headers, Set<String> requiredHeaders) {
        return headers != null
            && requiredHeaders != null
            && !requiredHeaders.isEmpty()
            && headers.keySet().containsAll(requiredHeaders);
    }

    private int scoreHeaderColumns(Map<String, Integer> headers, Set<String> requiredHeaders) {
        int score = 0;
        for (String requiredHeader : requiredHeaders) {
            if (headers.containsKey(requiredHeader)) {
                score += 3;
            }
        }
        for (String header : headers.keySet()) {
            if (!requiredHeaders.contains(header)) {
                score += 1;
            }
        }
        return score;
    }

    private int resolveIdentityEndColumnIndex(Map<String, Integer> headers) {
        return headers == null || headers.isEmpty()
            ? 4
            : headers.values().stream().max(Comparator.naturalOrder()).orElse(4);
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
                || mergedRegion.getFirstColumn() <= layout.identityEndColumnIndex()) {
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
        for (int columnIndex = layout.identityEndColumnIndex() + 1; columnIndex < Math.max(metricRow.getLastCellNum(), layout.lastCellNumber()); columnIndex += 1) {
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

            Map<String, String> identityValues = new LinkedHashMap<>();
            layout.identityColumnIndexes().forEach((identityKey, columnIndex) -> {
                String identityValue = blankToNull(
                    normalizeCellText(rowCell(row, columnIndex), formatter, evaluator)
                );
                if (identityValue != null) {
                    identityValues.put(identityKey, identityValue);
                }
            });

            List<ParsedDashboardCell> cells = new ArrayList<>();
            for (MetricColumn metricColumn : metricColumns) {
                Cell cell = row.getCell(metricColumn.columnIndex());
                String rawValue = blankToNull(normalizeCellText(cell, formatter, evaluator));
                String commentText = parseComment(cell);
                if (rawValue == null && commentText == null) {
                    continue;
                }
                if (isIgnoredSemanticMeasurementValue(rawValue) && commentText == null) {
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

            if (identityValues.isEmpty() && cells.isEmpty()) {
                continue;
            }

            rows.add(new ParsedDashboardRow(
                rowIndex + 1,
                identityValues,
                List.copyOf(cells)
            ));
        }
        return List.copyOf(rows);
    }

    private LocalDate parseDateCell(Cell cell, int rowNumber, FormulaEvaluator evaluator) {
        if (cell == null) {
            throw invalidSpreadsheet("Row " + rowNumber + ": Date column is blank.");
        }

        CellType effectiveType = effectiveCellType(cell);
        if (effectiveType == CellType.NUMERIC) {
            double numericDate = cell.getNumericCellValue();
            return DateUtil.getLocalDateTime(numericDate).toLocalDate();
        }

        String rawValue = normalizeCellText(cell, new DataFormatter(Locale.US), evaluator);
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
        CellType effectiveType = effectiveCellType(cell);
        if (effectiveType == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double numericValue = cell.getNumericCellValue();
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

    private Cell rowCell(Row row, int columnIndex) {
        return row == null || columnIndex < 0 ? null : row.getCell(columnIndex);
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
        if (cell.getCellType() == CellType.FORMULA) {
            return formatFormulaCellFromCache(cell, formatter);
        }
        String value = formatter.formatCellValue(cell, evaluator);
        return value == null ? "" : value.strip();
    }

    private CellType effectiveCellType(Cell cell) {
        if (cell == null) {
            return CellType.BLANK;
        }
        return cell.getCellType() == CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
    }

    private String formatFormulaCellFromCache(Cell cell, DataFormatter formatter) {
        CellType cachedType = effectiveCellType(cell);
        return switch (cachedType) {
            case NUMERIC -> formatter.formatRawCellContents(
                cell.getNumericCellValue(),
                cell.getCellStyle() == null ? -1 : cell.getCellStyle().getDataFormat(),
                cell.getCellStyle() == null ? null : cell.getCellStyle().getDataFormatString()
            ).strip();
            case STRING -> {
                String value = cell.getStringCellValue();
                yield value == null ? "" : value.strip();
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case BLANK, ERROR, _NONE -> "";
            default -> "";
        };
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String resolveCanonicalHeader(String normalizedHeader, Map<String, String> headerAliases) {
        if (normalizedHeader == null || normalizedHeader.isBlank()) {
            return null;
        }
        return headerAliases == null ? null : headerAliases.get(normalizedHeader);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeKey(String value) {
        return LocationDashboardGraphMetadataSupport.normalizeKey(value);
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
        Map<String, Integer> identityColumnIndexes,
        int identityEndColumnIndex,
        int lastCellNumber
    ) {
    }

    private record HeaderIdentityPattern(
        Map<String, String> headerAliases,
        Set<String> requiredHeaders
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
        Map<String, String> identityValues,
        List<ParsedDashboardCell> cells
    ) {
        public ParsedDashboardRow {
            identityValues = identityValues == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(identityValues));
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
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
