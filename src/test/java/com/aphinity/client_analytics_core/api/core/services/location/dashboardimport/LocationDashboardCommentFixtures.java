package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocationDashboardCommentFixtures {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private LocationDashboardCommentFixtures() {
    }

    public static String workbookStyleComment(String... lines) {
        return String.join("\n", lines);
    }

    public static StructuredAction correctiveAction(String text) {
        return new StructuredAction(null, text, null, List.of());
    }

    public static StructuredSample sample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit
    ) {
        return new StructuredSample(
            sampledOn,
            resultReceivedOn,
            resultRaw,
            resultValue,
            resultUnit,
            List.of(),
            List.of(),
            null
        );
    }

    public static StructuredSample sample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit,
        List<String> notes,
        List<StructuredAction> correctiveActions
    ) {
        return new StructuredSample(
            sampledOn,
            resultReceivedOn,
            resultRaw,
            resultValue,
            resultUnit,
            notes,
            correctiveActions,
            null
        );
    }

    public static String structuredComment(StructuredCommentSpec spec) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(structuredCommentMap(spec));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize structured comment fixture", ex);
        }
    }

    static String structuredComment(LocationDashboardCommentParser.ParsedComment parsedComment) {
        return structuredComment(new StructuredCommentSpec(
            parsedComment.sampleLocation(),
            toStructuredSample(parsedComment.primarySample()),
            parsedComment.followUpSamples().stream().map(LocationDashboardCommentFixtures::toStructuredSample).toList(),
            parsedComment.correctiveActions().stream().map(LocationDashboardCommentFixtures::toStructuredAction).toList(),
            parsedComment.notes()
        ));
    }

    private static StructuredSample toStructuredSample(LocationDashboardCommentParser.ParsedCommentSample sample) {
        if (sample == null) {
            return null;
        }
        return new StructuredSample(
            sample.sampledOn(),
            sample.resultReceivedOn(),
            sample.resultRaw(),
            sample.resultValue(),
            sample.resultUnit(),
            sample.notes(),
            sample.correctiveActions().stream().map(LocationDashboardCommentFixtures::toStructuredAction).toList(),
            null
        );
    }

    private static StructuredAction toStructuredAction(LocationDashboardCommentParser.ParsedCommentCorrectiveAction action) {
        return new StructuredAction(action.actionDate(), action.text(), action.ticket(), action.notes());
    }

    private static Map<String, Object> structuredCommentMap(StructuredCommentSpec spec) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", LocationDashboardCommentParser.SCHEMA_VERSION);
        root.put("sampleLocation", spec.sampleLocation());
        root.put("primarySample", sampleMap(spec.primarySample()));
        root.put("followUpSamples", spec.followUpSamples().stream().map(LocationDashboardCommentFixtures::sampleMap).toList());
        root.put("correctiveActions", spec.correctiveActions().stream().map(LocationDashboardCommentFixtures::actionMap).toList());
        root.put("notes", spec.notes());
        return root;
    }

    private static Map<String, Object> sampleMap(StructuredSample sample) {
        if (sample == null) {
            return null;
        }
        Map<String, Object> sampleMap = new LinkedHashMap<>();
        sampleMap.put("sampledOn", toText(sample.sampledOn()));
        sampleMap.put("resultReceivedOn", toText(sample.resultReceivedOn()));
        sampleMap.put("resultRaw", sample.resultRaw());
        sampleMap.put("resultValue", sample.resultValue());
        sampleMap.put("resultUnit", sample.resultUnit());
        sampleMap.put("notes", sample.notes());
        sampleMap.put("correctiveActions", sample.correctiveActions().stream().map(LocationDashboardCommentFixtures::actionMap).toList());
        sampleMap.put("sampleLocation", sample.sampleLocation());
        return sampleMap;
    }

    private static Map<String, Object> actionMap(StructuredAction action) {
        Map<String, Object> actionMap = new LinkedHashMap<>();
        actionMap.put("actionDate", toText(action.actionDate()));
        actionMap.put("text", action.text());
        actionMap.put("ticket", action.ticket());
        actionMap.put("notes", action.notes());
        return actionMap;
    }

    private static String toText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public record StructuredCommentSpec(
        String sampleLocation,
        StructuredSample primarySample,
        List<StructuredSample> followUpSamples,
        List<StructuredAction> correctiveActions,
        List<String> notes
    ) {
        public StructuredCommentSpec {
            followUpSamples = followUpSamples == null ? List.of() : List.copyOf(followUpSamples);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    public record StructuredSample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit,
        List<String> notes,
        List<StructuredAction> correctiveActions,
        String sampleLocation
    ) {
        public StructuredSample {
            notes = notes == null ? List.of() : List.copyOf(notes);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
        }
    }

    public record StructuredAction(
        LocalDate actionDate,
        String text,
        String ticket,
        List<String> notes
    ) {
        public StructuredAction {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }
}
