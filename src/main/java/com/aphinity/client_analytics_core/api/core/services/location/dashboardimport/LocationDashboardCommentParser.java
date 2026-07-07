package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocationDashboardCommentParser {
    private static final Pattern DATE_TOKEN_PATTERN = Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{2,4})\\b");
    private static final Pattern BARE_DATE_LINE_PATTERN = Pattern.compile("(?i)^\\d{1,2}/\\d{1,2}/\\d{2,4}[\\p{Punct}]?$");
    private static final Pattern YEAR_TOKEN_PATTERN = Pattern.compile("^(\\d{1,2}/\\d{1,2}/)(\\d{3,4})$");
    private static final Pattern RESAMPLE_ORDINAL_PREFIX_PATTERN = Pattern.compile(
        "^(?:\\d+(?:st|nd|rd|th)?|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)\\s+(?=(?:re-?sample|retest)\\b)"
    );
    private static final Pattern LABELED_TEST_NOTE_PATTERN = Pattern.compile(
        "(?i)^(first|second|third|fourth|fifth)\\s+test\\s*:\\s*(.+)$"
    );
    private static final Pattern MEASUREMENT_VALUE_PATTERN = Pattern.compile(
        "^(?:[<>]=?\\s*)?([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(?:\\s+(.+))?$",
        Pattern.CASE_INSENSITIVE
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
    private final LocationDashboardMeasurementUnitNormalizer measurementUnits;

    LocationDashboardCommentParser() {
        this(List.of());
    }

    LocationDashboardCommentParser(List<LocationDashboardImportStrategyConfig.MeasurementUnitConfig> measurementUnits) {
        this.measurementUnits = new LocationDashboardMeasurementUnitNormalizer(measurementUnits);
    }

    String unitForMeasurementName(String measurementName) {
        return measurementUnits.forMeasurementName(measurementName);
    }

    /**
     * Entry point for workbook comment text. This method normalizes the raw text, strips an optional
     * leading {@code Comment:} marker, rejects blank payloads, and skips compact semicolon-delimited
     * comments that are handled by the worksheet row data instead of by this free-text parser.
     */
    ParsedComment parse(String rawCommentText) {
        if (rawCommentText == null || rawCommentText.isBlank()) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }

        String payload = extractPayload(rawCommentText);
        if (payload == null || payload.isBlank()) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }

        String trimmed = payload.strip();
        if (isCompactSemicolonComment(trimmed)) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }
        return parseLegacyComment(payload);
    }

    /**
     * Parses a pre-screened legacy free-text payload. Unlike {@link #parse(String)}, this method assumes
     * the caller already removed workbook comment wrapping and filtered unsupported compact formats; it
     * focuses only on extracting sample location, primary/follow-up samples, corrective actions, and
     * leftover notes from inconsistent line-oriented legacy text.
     * This is the most commonly used parser. Shorthand parsing is deprecated and unlikely to supported in the future.
     */
    private ParsedComment parseLegacyComment(String payload) {
        String[] lines = payload.replace("\r\n", "\n").split("\\R");
        String sampleLocation = null;
        ParsedSampleAccumulator parsedSamples = new ParsedSampleAccumulator();
        List<ParsedCommentCorrectiveAction> correctiveActions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // Legacy comment text is inconsistent, so this parser walks the lines as a small state machine.
        MutableSample currentSample = null;
        MutableCorrectiveAction currentAction = null;
        LocalDate pendingSampleDate = null;

        for (String rawLine : lines) {
            String line = rawLine == null ? null : rawLine.strip();
            if (line == null || line.isBlank()) {
                continue;
            }

            ParsedLabeledTestNote labeledTestNote = parseLabeledTestNote(line);
            if (labeledTestNote != null) {
                if (currentSample != null && currentSample.resultValue != null) {
                    currentAction = finalizeAction(currentAction, currentSample, correctiveActions);
                    currentSample = finalizeSample(currentSample, parsedSamples);
                }
                if (currentSample == null) {
                    currentSample = new MutableSample();
                }
                if (currentSample.resultRaw == null) {
                    currentSample.resultRaw = labeledTestNote.measurement().rawValue();
                    currentSample.resultValue = labeledTestNote.measurement().value();
                    currentSample.resultUnit = labeledTestNote.measurement().unit();
                }
                currentSample.notes.add(line);
                continue;
            }

            if (isSampleLocationLine(line)) {
                sampleLocation = extractLocationValue(line);
                continue;
            }

            if (isSampleStartLine(line)) {
                currentAction = finalizeAction(currentAction, currentSample, correctiveActions);
                currentSample = finalizeSample(currentSample, parsedSamples);

                SampleStart start = parseSampleStart(line, pendingSampleDate);
                pendingSampleDate = null;
                if (start != null) {
                    currentSample = new MutableSample();
                    currentSample.sampledOn = start.sampledOn();
                    if (start.trailingText() != null && !start.trailingText().isBlank()) {
                        if (sampleLocation == null && looksLikeLocationLine(start.trailingText())) {
                            sampleLocation = start.trailingText();
                        } else {
                            currentSample.notes.add(start.trailingText());
                        }
                    }
                } else {
                    pendingSampleDate = parseDateToken(line);
                }
                continue;
            }

            if (isResultDateLine(line)) {
                LocalDate resultDate = parseDateFromLine(line);
                if (resultDate != null) {
                    if (currentSample != null && currentSample.resultReceivedOn == null) {
                        currentSample.resultReceivedOn = resultDate;
                    } else {
                        notes.add(line);
                    }
                } else {
                    notes.add(line);
                }
                continue;
            }

            if (isResultLine(line)) {
                String rawValue = extractLineValue(line);
                if (isIgnoredSemanticMeasurementValue(rawValue)) {
                    continue;
                }
                if (currentSample == null && pendingSampleDate != null) {
                    currentSample = new MutableSample();
                    currentSample.sampledOn = pendingSampleDate;
                    pendingSampleDate = null;
                }
                if (currentSample != null) {
                    ParsedMeasurement measurement = parseMeasurementValue(rawValue);
                    if (measurement != null && currentSample.resultRaw == null) {
                        currentSample.resultRaw = measurement.rawValue();
                        currentSample.resultValue = measurement.value();
                        currentSample.resultUnit = measurement.unit();
                    } else {
                        currentSample.notes.add(line);
                    }
                } else {
                    notes.add(line);
                }
                continue;
            }

            if (isCorrectiveActionLine(line)) {
                currentAction = finalizeAction(currentAction, currentSample, correctiveActions);
                currentAction = new MutableCorrectiveAction();
                ParsedAction parsedAction = parseActionLine(line);
                currentAction.actionDate = parsedAction.actionDate();
                currentAction.text = parsedAction.text();
                if (currentAction.text == null || currentAction.text.isBlank()) {
                    currentAction.text = null;
                }
                continue;
            }

            if (isTicketLine(line)) {
                if (currentAction != null) {
                    currentAction.ticket = extractTicket(line);
                } else {
                    notes.add(line);
                }
                continue;
            }

            LocalDate bareDate = parseDateToken(line);
            if (bareDate != null && BARE_DATE_LINE_PATTERN.matcher(line).matches()) {
                pendingSampleDate = bareDate;
                continue;
            }

            if (currentAction != null && currentAction.text == null) {
                currentAction.text = line;
                continue;
            }
            if (currentAction != null) {
                currentAction.notes.add(line);
                continue;
            }

            if (currentSample != null) {
                if (currentSample.sampleLocation == null && looksLikeLocationLine(line)) {
                    currentSample.sampleLocation = line;
                } else {
                    currentSample.notes.add(line);
                }
                continue;
            }

            if (sampleLocation == null && looksLikeLocationLine(line)) {
                sampleLocation = line;
                continue;
            }

            if (looksLikeActionText(line)) {
                correctiveActions.add(new ParsedCommentCorrectiveAction(null, line, null, List.of()));
                continue;
            }

            notes.add(line);
        }

        currentAction = finalizeAction(currentAction, currentSample, correctiveActions);
        currentSample = finalizeSample(currentSample, parsedSamples);
        appendFallbackRetestSampleIfMissing(lines, parsedSamples);

        return new ParsedComment(
            false,
            sampleLocation,
            parsedSamples.primarySample,
            parsedSamples.followUpSamples,
            correctiveActions,
            notes
        );
    }

    private MutableCorrectiveAction finalizeAction(
        MutableCorrectiveAction currentAction,
        MutableSample currentSample,
        List<ParsedCommentCorrectiveAction> correctiveActions
    ) {
        if (currentAction == null) {
            return null;
        }
        if (currentAction.text == null && currentAction.notes.isEmpty() && currentAction.ticket == null) {
            return null;
        }
        ParsedCommentCorrectiveAction action = currentAction.toRecord();
        if (currentSample != null) {
            currentSample.correctiveActions.add(action);
        } else {
            correctiveActions.add(action);
        }
        return null;
    }

    private MutableSample finalizeSample(MutableSample currentSample, ParsedSampleAccumulator parsedSamples) {
        if (currentSample == null) {
            return null;
        }
        if (!currentSample.hasContent()) {
            return null;
        }
        parsedSamples.add(currentSample.toRecord());
        return null;
    }

    private void appendFallbackRetestSampleIfMissing(String[] lines, ParsedSampleAccumulator parsedSamples) {
        ParsedCommentSample fallbackSample = parseFallbackRetestSample(lines);
        if (fallbackSample == null) {
            return;
        }
        for (ParsedCommentSample existingSample : parsedSamples.allSamples()) {
            if (existingSample == null) {
                continue;
            }
            if (java.util.Objects.equals(existingSample.sampledOn(), fallbackSample.sampledOn())
                && java.util.Objects.equals(existingSample.resultReceivedOn(), fallbackSample.resultReceivedOn())
                && java.util.Objects.equals(existingSample.resultValue(), fallbackSample.resultValue())) {
                return;
            }
        }
        parsedSamples.addFollowUp(fallbackSample);
    }

    private ParsedCommentSample parseFallbackRetestSample(String[] lines) {
        if (lines == null || lines.length == 0) {
            return null;
        }

        MutableSample currentRetest = null;
        for (String rawLine : lines) {
            String line = rawLine == null ? null : rawLine.strip();
            if (line == null || line.isBlank()) {
                continue;
            }

            if (isExplicitRetestSampleStartLine(line)) {
                if (currentRetest != null && currentRetest.sampledOn != null && currentRetest.resultValue != null) {
                    return currentRetest.toRecord();
                }
                LocalDate sampleDate = parseDateToken(line);
                if (sampleDate == null) {
                    currentRetest = null;
                    continue;
                }
                currentRetest = new MutableSample();
                currentRetest.sampledOn = sampleDate;
                continue;
            }

            if (currentRetest == null) {
                continue;
            }

            if (isResultDateLine(line)) {
                LocalDate resultDate = parseDateFromLine(line);
                if (resultDate != null && currentRetest.resultReceivedOn == null) {
                    currentRetest.resultReceivedOn = resultDate;
                }
                continue;
            }

            if (isResultLine(line)) {
                ParsedMeasurement measurement = parseMeasurementValue(extractLineValue(line));
                if (measurement != null && currentRetest.resultValue == null) {
                    currentRetest.resultRaw = measurement.rawValue();
                    currentRetest.resultValue = measurement.value();
                    currentRetest.resultUnit = measurement.unit();
                }
                continue;
            }
        }

        if (currentRetest == null || currentRetest.sampledOn == null || currentRetest.resultValue == null) {
            return null;
        }
        return currentRetest.toRecord();
    }

    private ParsedMeasurement parseMeasurementValue(String rawValue) {
        String cleanedValue = getCleanedValue(rawValue);
        if (cleanedValue == null) return null;

        if (cleanedValue.equalsIgnoreCase("nd")
            || cleanedValue.equalsIgnoreCase("n.d.")
            || cleanedValue.equalsIgnoreCase("not detected")) {
            return new ParsedMeasurement(BigDecimal.ZERO, null, "ND");
        }

        if (isIgnoredSemanticMeasurementValue(cleanedValue)) {
            return null;
        }

        Matcher matcher = MEASUREMENT_VALUE_PATTERN.matcher(cleanedValue);
        if (!matcher.matches()) {
            return null;
        }

        String numericPortion = matcher.group(1);
        if (numericPortion == null) {
            return null;
        }
        try {
            BigDecimal numericValue = new BigDecimal(normalizeNumericPortion(numericPortion));
            String unit = normalizeUnit(matcher.group(2));
            String normalizedRaw = numericValue.toPlainString();
            return new ParsedMeasurement(numericValue, unit, normalizedRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static @Nullable String getCleanedValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String cleanedValue = rawValue.replace(",", "").strip();
        if (cleanedValue.isBlank()) {
            return null;
        }
        return cleanedValue;
    }

    private static List<BigDecimal> parseLabeledTestResults(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> results = new ArrayList<>();
        for (String note : notes) {
            if (note == null || note.isBlank()) {
                continue;
            }
            Matcher matcher = LABELED_TEST_NOTE_PATTERN.matcher(note.strip());
            if (!matcher.matches()) {
                continue;
            }
            ParsedMeasurement measurement = parseStaticMeasurementValue(matcher.group(2));
            if (measurement != null && measurement.value() != null) {
                results.add(measurement.value());
            }
        }
        return List.copyOf(results);
    }

    private ParsedLabeledTestNote parseLabeledTestNote(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher matcher = LABELED_TEST_NOTE_PATTERN.matcher(line.strip());
        if (!matcher.matches()) {
            return null;
        }
        ParsedMeasurement measurement = parseMeasurementValue(matcher.group(2));
        if (measurement == null || measurement.value() == null) {
            return null;
        }
        return new ParsedLabeledTestNote(matcher.group(1).toLowerCase(Locale.ROOT), measurement);
    }

    private static ParsedMeasurement parseStaticMeasurementValue(String rawValue) {
        String cleanedValue = getCleanedValue(rawValue);
        if (cleanedValue == null) return null;
        if (cleanedValue.equalsIgnoreCase("nd")
            || cleanedValue.equalsIgnoreCase("n.d.")
            || cleanedValue.equalsIgnoreCase("not detected")) {
            return new ParsedMeasurement(BigDecimal.ZERO, null, "ND");
        }
        if (cleanedValue.equalsIgnoreCase("nt")
            || cleanedValue.equalsIgnoreCase("n.t.")
            || cleanedValue.equalsIgnoreCase("not tested")) {
            return null;
        }

        Matcher matcher = MEASUREMENT_VALUE_PATTERN.matcher(cleanedValue);
        if (!matcher.matches()) {
            return null;
        }

        String numericPortion = matcher.group(1);
        if (numericPortion == null) {
            return null;
        }
        try {
            BigDecimal numericValue = new BigDecimal(normalizeStaticNumericPortion(numericPortion));
            return new ParsedMeasurement(numericValue, null, numericValue.toPlainString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeStaticNumericPortion(String numericPortion) {
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

    private String normalizeUnit(String rawUnit) {
        if (rawUnit == null) {
            return null;
        }
        return rawUnit == null || rawUnit.isBlank() ? null : rawUnit.strip();
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

    private SampleStart parseSampleStart(String line, LocalDate fallbackDate) {
        String normalized = line.strip();
        LocalDate date = parseDateToken(normalized);
        if (date == null) {
            date = fallbackDate;
        }
        if (date == null) {
            return null;
        }

        String trailingText = normalized;
        Matcher matcher = DATE_TOKEN_PATTERN.matcher(normalized);
        if (matcher.find()) {
            trailingText = normalized.substring(matcher.end()).strip();
        } else {
            int separatorIndex = Math.max(normalized.indexOf(':'), normalized.indexOf('-'));
            if (separatorIndex >= 0) {
                trailingText = normalized.substring(separatorIndex + 1).strip();
            }
        }
        trailingText = blankToNull(trimTrailingPunctuation(trailingText));
        if (trailingText != null && looksLikeLocationLine(trailingText) && !isCorrectiveActionLine(trailingText)) {
            return new SampleStart(date, trailingText);
        }
        return new SampleStart(date, trailingText);
    }

    private LocalDate parseDateToken(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        Matcher matcher = DATE_TOKEN_PATTERN.matcher(rawValue.strip());
        if (!matcher.find()) {
            return null;
        }
        return parseDate(matcher.group(1));
    }

    private LocalDate parseDateFromLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = DATE_TOKEN_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        return parseDate(matcher.group(1));
    }

    private LocalDate parseDate(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalizedText = rawValue.strip().replaceAll("[\\.,;]+$", "");
        if (normalizedText.isBlank()) {
            return null;
        }
        normalizedText = normalizeYearToken(normalizedText);
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalizedText, dateFormatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String normalizeYearToken(String rawValue) {
        Matcher matcher = YEAR_TOKEN_PATTERN.matcher(rawValue);
        if (!matcher.matches()) {
            return rawValue;
        }
        String yearToken = matcher.group(2);
        if (yearToken.length() == 3 && yearToken.startsWith("0")) {
            return matcher.group(1) + yearToken.substring(1);
        }
        return rawValue;
    }

    private String extractPayload(String rawCommentText) {
        String normalized = rawCommentText.replace("\r\n", "\n").strip();
        int markerIndex = normalized.indexOf("Comment:");
        if (markerIndex >= 0) {
            return normalized.substring(markerIndex + "Comment:".length()).stripLeading();
        }
        return normalized;
    }

    private boolean isCompactSemicolonComment(String payload) {
        return payload != null && payload.contains(";") && !payload.contains("\n") && !payload.startsWith("{");
    }

    private boolean isSampleLocationLine(String line) {
        String normalized = normalizeKey(line);
        return normalized.startsWith("sample location")
            || normalized.startsWith("location ")
            || normalized.startsWith("location-")
            || normalized.startsWith("location:");
    }

    private boolean isSampleStartLine(String line) {
        String normalized = normalizeResampleLabelKey(line);
        return normalized.startsWith("sampled ")
            || normalized.startsWith("sample date")
            || normalized.startsWith("date of sampling")
            || normalized.startsWith("first sample taken on")
            || normalized.startsWith("retest sample date")
            || normalized.startsWith("retest sample taken on")
            || normalized.startsWith("retest sampled")
            || normalized.startsWith("retest date")
            || normalized.startsWith("re-sample date")
            || normalized.startsWith("resample date")
            || normalized.startsWith("date of resampling")
            || normalized.startsWith("resampled")
            || normalized.startsWith("first retest sample taken on")
            || normalized.startsWith("second retest sample taken on");
    }

    private boolean isExplicitRetestSampleStartLine(String line) {
        String normalized = normalizeResampleLabelKey(line);
        return normalized.startsWith("retest sample date")
            || normalized.startsWith("retest sample taken on")
            || normalized.startsWith("retest sampled")
            || normalized.startsWith("retest date")
            || normalized.startsWith("re-sample date")
            || normalized.startsWith("resample date")
            || normalized.startsWith("date of resampling")
            || normalized.startsWith("resampled")
            || normalized.startsWith("first retest sample taken on")
            || normalized.startsWith("second retest sample taken on");
    }

    private boolean isResultDateLine(String line) {
        String normalized = normalizeResampleLabelKey(line);
        return normalized.startsWith("result date")
            || normalized.startsWith("results date")
            || normalized.startsWith("results received")
            || normalized.startsWith("result received")
            || normalized.startsWith("re-sample results date")
            || normalized.startsWith("resample results date")
            || normalized.startsWith("retest result date")
            || normalized.startsWith("date of results");
    }

    private boolean isResultLine(String line) {
        String normalized = normalizeResampleLabelKey(line);
        if (normalized.startsWith("result date") /* this seems to hit 'date' values too? */
            || normalized.startsWith("results date")
            || normalized.startsWith("re-sample results date")
            || normalized.startsWith("resample results date")
            || normalized.startsWith("retest result date")
            || normalized.startsWith("results received")
            || normalized.startsWith("result received")
            || normalized.startsWith("date of results")) /* this seems to hit 'date' values too? */  {
            return false;
        }
        if (normalized.startsWith("result")
            || normalized.startsWith("re-sample result")
            || normalized.startsWith("resample result")
            || normalized.startsWith("retest result")
            || normalized.startsWith("first sample result")
            || normalized.startsWith("second test")) {
            return true;
        }
        return parseMeasurementValue(extractLineValue(line)) != null;
    }

    private boolean isCorrectiveActionLine(String line) {
        String normalized = normalizeKey(line);
        return normalized.startsWith("action:")
            || normalized.startsWith("action ")
            || normalized.startsWith("action taken")
            || normalized.startsWith("corrective action")
            || normalized.startsWith("short-term action")
            || normalized.startsWith("long-term action");
    }

    private ParsedAction parseActionLine(String line) {
        String normalized = line.strip();
        String value = extractLineValue(normalized);
        LocalDate actionDate = parseDateToken(value);
        if (actionDate != null) {
            Matcher matcher = DATE_TOKEN_PATTERN.matcher(value);
            if (matcher.find()) {
                String beforeDate = blankToNull(trimTrailingPunctuation(value.substring(0, matcher.start()).strip()));
                String afterDate = blankToNull(trimTrailingPunctuation(value.substring(matcher.end()).strip()));
                String actionText = beforeDate == null ? afterDate : beforeDate;
                if (afterDate != null && beforeDate != null) {
                    actionText = beforeDate + " " + afterDate;
                }
                return new ParsedAction(actionDate, actionText);
            }
            return new ParsedAction(actionDate, blankToNull(trimTrailingPunctuation(value)));
        }

        LocalDate inlineDate = parseDateToken(normalized);
        if (inlineDate != null) {
            String stripped = trimLeadingLabel(normalized);
            String afterDate = stripped.replaceFirst(DATE_TOKEN_PATTERN.pattern(), "").strip();
            afterDate = blankToNull(trimTrailingPunctuation(afterDate));
            return new ParsedAction(inlineDate, afterDate);
        }

        return new ParsedAction(null, blankToNull(trimTrailingPunctuation(value)));
    }

    private boolean isTicketLine(String line) {
        String normalized = normalizeKey(line);
        return normalized.startsWith("ticket:")
            || normalized.startsWith("ticket ")
            || normalized.matches("^fcr\\d+.*");
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

    private String extractTicket(String line) {
        String value = extractLineValue(line);
        if (value == null || value.isBlank()) {
            return blankToNull(line);
        }
        return value.strip();
    }

    private String extractLineValue(String line) {
        if (line == null) {
            return null;
        }
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0) {
            return blankToNull(line.substring(colonIndex + 1));
        }
        int hyphenIndex = line.indexOf('-');
        if (hyphenIndex >= 0 && hyphenIndex < 20) {
            return blankToNull(line.substring(hyphenIndex + 1));
        }
        Matcher matcher = DATE_TOKEN_PATTERN.matcher(line);
        if (matcher.find()) {
            return blankToNull(line.substring(matcher.end()));
        }
        return blankToNull(line);
    }

    private String extractLocationValue(String line) {
        if (line == null) {
            return null;
        }
        String normalized = line.strip();
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("sample location")) {
            return blankToNull(normalized.substring("sample location".length()).replaceFirst("^[\\s:-]+", ""));
        }
        if (lowered.startsWith("location")) {
            return blankToNull(normalized.substring("location".length()).replaceFirst("^[\\s:-]+", ""));
        }
        return extractLineValue(line);
    }

    private String trimLeadingLabel(String line) {
        if (line == null) {
            return null;
        }
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0) {
            return line.substring(colonIndex + 1).strip();
        }
        return line.strip();
    }

    private String trimTrailingPunctuation(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\s\\p{Punct}]+$", "").strip();
    }

    private boolean looksLikeActionText(String line) {
        String normalized = normalizeKey(line);
        if (normalized.isBlank() || Character.isDigit(normalized.charAt(0))) {
            return false;
        }
        return normalized.contains("install")
            || normalized.contains("replace")
            || normalized.contains("flush")
            || normalized.contains("exchange")
            || normalized.contains("remove")
            || normalized.contains("change")
            || normalized.contains("disinfect")
            || normalized.contains("clean")
            || normalized.contains("raise")
            || normalized.contains("lower")
            || normalized.contains("repair")
            || normalized.contains("drain")
            || normalized.contains("fill")
            || normalized.contains("wipe")
            || normalized.contains("chlorin");
    }

    private boolean looksLikeLocationLine(String line) {
        String normalized = normalizeKey(line);
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("action")
            || normalized.contains("result")
            || normalized.contains("ticket")
            || normalized.contains("flush")
            || normalized.contains("replace")
            || normalized.contains("install")) {
            return false;
        }
        if (normalized.startsWith("sample location") || normalized.startsWith("location ")) {
            return true;
        }
        if (line.contains(":") || line.endsWith(".")) {
            return false;
        }
        return normalized.length() <= 80
            && (normalized.contains("sink")
            || normalized.contains("room")
            || normalized.contains("wing")
            || normalized.contains("pod")
            || normalized.contains("tower")
            || normalized.contains("treatment")
            || normalized.contains("location")
            || normalized.contains("source")
            || normalized.contains("bottle")
            || normalized.contains("filter")
            || normalized.contains("line")
            || normalized.contains("port")
            || normalized.contains("water"));
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizeResampleLabelKey(String value) {
        String normalized = normalizeKey(value);
        return RESAMPLE_ORDINAL_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private record ParsedMeasurement(
        BigDecimal value,
        String unit,
        String rawValue
    ) {
    }

    private record SampleStart(
        LocalDate sampledOn,
        String trailingText
    ) {
    }

    private record ParsedAction(
        LocalDate actionDate,
        String text
    ) {
    }

    private record ParsedLabeledTestNote(
        String ordinal,
        ParsedMeasurement measurement
    ) {
    }

    private static final class MutableSample {
        private LocalDate sampledOn;
        private LocalDate resultReceivedOn;
        private String resultRaw;
        private BigDecimal resultValue;
        private String resultUnit;
        private String sampleLocation;
        private final List<String> notes = new ArrayList<>();
        private final List<ParsedCommentCorrectiveAction> correctiveActions = new ArrayList<>();

        private boolean hasContent() {
            return sampledOn != null
                || resultReceivedOn != null
                || resultRaw != null
                || resultValue != null
                || resultUnit != null
                || sampleLocation != null
                || !notes.isEmpty()
                || !correctiveActions.isEmpty();
        }

        private ParsedCommentSample toRecord() {
            return new ParsedCommentSample(
                sampledOn,
                resultReceivedOn,
                resultRaw,
                resultValue,
                resultUnit,
                notes,
                correctiveActions
            );
        }
    }

    private static final class MutableCorrectiveAction {
        private LocalDate actionDate;
        private String text;
        private String ticket;
        private final List<String> notes = new ArrayList<>();

        private ParsedCommentCorrectiveAction toRecord() {
            return new ParsedCommentCorrectiveAction(
                actionDate,
                text,
                ticket,
                notes
            );
        }
    }

    private static final class ParsedSampleAccumulator {
        private ParsedCommentSample primarySample;
        private final List<ParsedCommentSample> followUpSamples = new ArrayList<>();

        private void add(ParsedCommentSample sample) {
            if (sample == null) {
                return;
            }
            if (primarySample == null) {
                primarySample = sample;
                return;
            }
            followUpSamples.add(sample);
        }

        private void addFollowUp(ParsedCommentSample sample) {
            if (sample == null) {
                return;
            }
            followUpSamples.add(sample);
        }

        private List<ParsedCommentSample> allSamples() {
            List<ParsedCommentSample> samples = new ArrayList<>();
            if (primarySample != null) {
                samples.add(primarySample);
            }
            samples.addAll(followUpSamples);
            return List.copyOf(samples);
        }
    }

    record ParsedComment(
        boolean structured,
        String sampleLocation,
        ParsedCommentSample primarySample,
        List<ParsedCommentSample> followUpSamples,
        List<ParsedCommentCorrectiveAction> correctiveActions,
        List<String> notes
    ) {
        ParsedComment {
            followUpSamples = followUpSamples == null ? List.of() : List.copyOf(followUpSamples);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        List<ParsedCommentSample> allSamples() {
            List<ParsedCommentSample> samples = new ArrayList<>();
            if (primarySample != null) {
                samples.add(primarySample);
            }
            samples.addAll(followUpSamples);
            return List.copyOf(samples);
        }

        boolean hasMeaningfulContent() {
            return sampleLocation != null
                || primarySample != null
                || !followUpSamples.isEmpty()
                || !correctiveActions.isEmpty()
                || !notes.isEmpty();
        }

        private List<String> migratedCompatibilityNotes() {
            List<String> compatibilityNotes = new ArrayList<>(notes);
            if (primarySample != null && primarySample.notes() != null && !primarySample.notes().isEmpty()) {
                compatibilityNotes.addAll(primarySample.notes());
            }
            return List.copyOf(compatibilityNotes);
        }

        List<BigDecimal> labeledTestResults() {
            return LocationDashboardCommentParser.parseLabeledTestResults(migratedCompatibilityNotes());
        }

        BigDecimal worksheetCompatibilityResultValue() {
            List<BigDecimal> labeledResults = labeledTestResults();
            if (!labeledResults.isEmpty()) {
                return labeledResults.getFirst();
            }
            return primarySample == null ? null : primarySample.resultValue();
        }

        BigDecimal effectiveAnchoredResultValue() {
            List<BigDecimal> labeledResults = labeledTestResults();
            if (labeledResults.size() >= 2) {
                return labeledResults.getLast();
            }
            if (primarySample != null && primarySample.resultValue() != null) {
                if (labeledResults.size() == 1
                    && labeledResults.getFirst().compareTo(primarySample.resultValue()) != 0) {
                    return primarySample.resultValue();
                }
                return primarySample.resultValue();
            }
            return labeledResults.isEmpty() ? null : labeledResults.getLast();
        }

        boolean matchesWorksheetCompatibilityValue(BigDecimal worksheetValue) {
            if (worksheetValue == null) {
                return false;
            }
            BigDecimal compatibilityValue = worksheetCompatibilityResultValue();
            if (compatibilityValue != null && worksheetValue.compareTo(compatibilityValue) == 0) {
                return true;
            }
            return primarySample != null
                && primarySample.resultValue() != null
                && worksheetValue.compareTo(primarySample.resultValue()) == 0;
        }

        boolean hasMigratedLabeledTestNotes() {
            for (String note : migratedCompatibilityNotes()) {
                if (note != null && LABELED_TEST_NOTE_PATTERN.matcher(note.strip()).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

    record ParsedCommentSample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit,
        List<String> notes,
        List<ParsedCommentCorrectiveAction> correctiveActions
    ) {
        ParsedCommentSample {
            notes = notes == null ? List.of() : List.copyOf(notes);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
        }
    }

    record ParsedCommentCorrectiveAction(
        LocalDate actionDate,
        String text,
        String ticket,
        List<String> notes
    ) {
        ParsedCommentCorrectiveAction {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }
}
