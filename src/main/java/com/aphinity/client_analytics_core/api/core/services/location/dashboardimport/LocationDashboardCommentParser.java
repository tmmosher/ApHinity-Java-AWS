package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

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
    static final String SCHEMA_VERSION = "aphinity.location-dashboard.comment.v1";

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final Pattern DATE_TOKEN_PATTERN = Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{2,4})\\b");
    private static final Pattern BARE_DATE_LINE_PATTERN = Pattern.compile("(?i)^\\d{1,2}/\\d{1,2}/\\d{2,4}[\\p{Punct}]?$");
    private static final Pattern YEAR_TOKEN_PATTERN = Pattern.compile("^(\\d{1,2}/\\d{1,2}/)(\\d{3,4})$");
    private static final Pattern MEASUREMENT_VALUE_PATTERN = Pattern.compile(
        "^[<>]=?\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(?:\\s+(.+))?$",
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

    ParsedComment parse(String rawCommentText) {
        if (rawCommentText == null || rawCommentText.isBlank()) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }

        String payload = extractPayload(rawCommentText);
        if (payload == null || payload.isBlank()) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }

        String trimmed = payload.strip();
        if (looksLikeStructuredJson(trimmed)) {
            return parseStructuredComment(trimmed);
        }
        // Preserve the old free-form formats only after structured JSON has been ruled out.
        if (isCompactSemicolonComment(trimmed)) {
            return new ParsedComment(false, null, null, List.of(), List.of(), List.of());
        }
        return parseLegacyComment(payload);
    }

    private ParsedComment parseStructuredComment(String jsonText) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonText);
            if (root == null || !root.isObject()) {
                throw invalidComment("Structured comment must be a JSON object.");
            }

            String schema = text(root, "schema");
            if (!SCHEMA_VERSION.equals(schema)) {
                throw invalidComment("Unsupported comment schema: " + schema);
            }

            String sampleLocation = blankToNull(text(root, "sampleLocation"));
            ParsedCommentSample primarySample = parseSample(
                root.get("primarySample"),
                true,
                "primarySample"
            );
            List<ParsedCommentSample> followUpSamples = parseSampleList(root.get("followUpSamples"));
            List<ParsedCommentCorrectiveAction> correctiveActions = parseCorrectiveActionList(root.get("correctiveActions"));
            List<String> notes = parseStringList(root.get("notes"));

            return new ParsedComment(
                true,
                sampleLocation,
                primarySample,
                followUpSamples,
                correctiveActions,
                notes
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidComment("Structured comment could not be parsed.");
        }
    }

    private ParsedComment parseLegacyComment(String payload) {
        String[] lines = payload.replace("\r\n", "\n").split("\\R");
        String sampleLocation = null;
        List<ParsedCommentSample> followUpSamples = new ArrayList<>();
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

            if (isSampleLocationLine(line)) {
                sampleLocation = extractLocationValue(line);
                continue;
            }

            if (isSampleStartLine(line)) {
                currentAction = finalizeAction(currentAction, currentSample, correctiveActions);
                currentSample = finalizeSample(currentSample, followUpSamples);

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
        currentSample = finalizeSample(currentSample, followUpSamples);

        return new ParsedComment(
            false,
            sampleLocation,
            null,
            followUpSamples,
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

    private MutableSample finalizeSample(MutableSample currentSample, List<ParsedCommentSample> followUpSamples) {
        if (currentSample == null) {
            return null;
        }
        if (!currentSample.hasContent()) {
            return null;
        }
        followUpSamples.add(currentSample.toRecord());
        return null;
    }

    private ParsedCommentSample parseSample(JsonNode node, boolean required, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (required) {
                throw invalidComment("Missing structured comment field: " + fieldName);
            }
            return null;
        }
        if (!node.isObject()) {
            throw invalidComment("Structured comment field must be an object: " + fieldName);
        }

        LocalDate sampledOn = parseRequiredDate(node, "sampledOn");
        LocalDate resultReceivedOn = parseOptionalDate(node, "resultReceivedOn");
        String resultRaw = blankToNull(text(node, "resultRaw"));
        BigDecimal resultValue = parseOptionalDecimal(node.get("resultValue"));
        String resultUnit = blankToNull(text(node, "resultUnit"));
        List<String> notes = parseStringList(node.get("notes"));
        List<ParsedCommentCorrectiveAction> correctiveActions = parseCorrectiveActionList(node.get("correctiveActions"));

        if (resultValue == null && resultRaw != null) {
            ParsedMeasurement measurement = parseMeasurementValue(resultRaw);
            if (measurement != null) {
                resultValue = measurement.value();
                if (resultUnit == null) {
                    resultUnit = measurement.unit();
                }
                resultRaw = measurement.rawValue();
            }
        }

        if (resultValue == null && resultRaw == null) {
            throw invalidComment("Structured comment sample is missing a result value.");
        }
        if (resultRaw == null && resultValue != null) {
            resultRaw = resultValue.toPlainString() + (resultUnit == null ? "" : " " + resultUnit);
        }

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

    private List<ParsedCommentSample> parseSampleList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        List<ParsedCommentSample> samples = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode sampleNode : node) {
                samples.add(parseSample(sampleNode, true, "followUpSamples[]"));
            }
            return samples;
        }
        samples.add(parseSample(node, true, "followUpSamples"));
        return samples;
    }

    private List<ParsedCommentCorrectiveAction> parseCorrectiveActionList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        List<ParsedCommentCorrectiveAction> actions = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode actionNode : node) {
                ParsedCommentCorrectiveAction action = parseCorrectiveAction(actionNode);
                if (action != null) {
                    actions.add(action);
                }
            }
            return actions;
        }
        ParsedCommentCorrectiveAction action = parseCorrectiveAction(node);
        if (action != null) {
            actions.add(action);
        }
        return actions;
    }

    private ParsedCommentCorrectiveAction parseCorrectiveAction(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String text = blankToNull(node.asText());
            if (text == null) {
                return null;
            }
            return new ParsedCommentCorrectiveAction(null, text, null, List.of());
        }
        if (!node.isObject()) {
            throw invalidComment("Structured corrective action must be a JSON object or string.");
        }

        LocalDate actionDate = parseOptionalDate(node, "actionDate");
        String text = blankToNull(text(node, "text"));
        String ticket = blankToNull(text(node, "ticket"));
        List<String> notes = parseStringList(node.get("notes"));
        if (text == null) {
            if (!notes.isEmpty()) {
                text = String.join(" ", notes);
                notes = List.of();
            } else {
                throw invalidComment("Structured corrective action is missing text.");
            }
        }
        return new ParsedCommentCorrectiveAction(actionDate, text, ticket, notes);
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = parseStringValue(item);
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }
        String value = parseStringValue(node);
        if (value != null) {
            values.add(value);
        }
        return values;
    }

    private String parseStringValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isObject()) {
            String text = blankToNull(text(node, "text"));
            if (text != null) {
                return text;
            }
        }
        String value = blankToNull(node.asText());
        return value;
    }

    private BigDecimal parseOptionalDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String rawValue = blankToNull(node.asText());
            if (rawValue == null) {
                return null;
            }
            ParsedMeasurement measurement = parseMeasurementValue(rawValue);
            if (measurement != null) {
                return measurement.value();
            }
            try {
                return new BigDecimal(rawValue);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private ParsedMeasurement parseMeasurementValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String cleanedValue = rawValue.replace(",", "").strip();
        if (cleanedValue.isBlank()) {
            return null;
        }

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
            String normalizedRaw = numericValue.toPlainString() + (unit == null ? "" : " " + unit);
            return new ParsedMeasurement(numericValue, unit, normalizedRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeUnit(String rawUnit) {
        if (rawUnit == null) {
            return null;
        }
        String normalized = rawUnit.strip().replaceAll("[\\s\\p{Punct}]+$", "");
        return normalized.isBlank() ? null : normalized;
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

    private LocalDate parseRequiredDate(JsonNode node, String field) {
        LocalDate date = parseOptionalDate(node, field);
        if (date == null) {
            throw invalidComment("Structured comment field is missing a valid date: " + field);
        }
        return date;
    }

    private LocalDate parseOptionalDate(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return parseDate(text(node, field));
    }

    private LocalDate parseOptionalDate(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : parseDate(node.asText());
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

    private boolean looksLikeStructuredJson(String payload) {
        return payload != null && payload.startsWith("{") && payload.endsWith("}");
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
        String normalized = normalizeKey(line);
        return normalized.startsWith("sampled ")
            || normalized.startsWith("sample date")
            || normalized.startsWith("date of sampling")
            || normalized.startsWith("first sample taken on")
            || normalized.startsWith("retest sample taken on")
            || normalized.startsWith("retest sampled")
            || normalized.startsWith("retest date")
            || normalized.startsWith("date of resampling")
            || normalized.startsWith("resampled")
            || normalized.startsWith("first retest sample taken on")
            || normalized.startsWith("second retest sample taken on");
    }

    private boolean isResultDateLine(String line) {
        String normalized = normalizeKey(line);
        return normalized.startsWith("result date")
            || normalized.startsWith("results received")
            || normalized.startsWith("result received")
            || normalized.startsWith("date of results");
    }

    private boolean isResultLine(String line) {
        String normalized = normalizeKey(line);
        if (normalized.startsWith("result date")
            || normalized.startsWith("results received")
            || normalized.startsWith("result received")
            || normalized.startsWith("date of results")) {
            return false;
        }
        if (normalized.startsWith("result")
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
            String remainder = matcher.find() ? value.substring(matcher.end()).strip() : value;
            remainder = blankToNull(trimTrailingPunctuation(remainder));
            if (remainder != null) {
                return new ParsedAction(actionDate, remainder);
            }
            return new ParsedAction(actionDate, null);
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

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            return null;
        }
        if (child.isTextual()) {
            return child.asText();
        }
        return child.asText(null);
    }

    private IllegalArgumentException invalidComment(String message) {
        return new IllegalArgumentException(message);
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
