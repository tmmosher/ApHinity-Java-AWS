package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LocationDashboardCommentFixtures {
    private static final DateTimeFormatter COMMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yy");

    private LocationDashboardCommentFixtures() {
    }

    public static String workbookStyleComment(String... lines) {
        return String.join("\n", lines);
    }

    public static WorkbookAction correctiveAction(String text) {
        return new WorkbookAction(null, text, null, List.of());
    }

    public static WorkbookSample sample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit
    ) {
        return new WorkbookSample(
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

    public static WorkbookSample sample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit,
        List<String> notes,
        List<WorkbookAction> correctiveActions
    ) {
        return new WorkbookSample(
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

    public static String workbookComment(WorkbookCommentSpec spec) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, prefixedLine("Sample Location", spec.sampleLocation()));
        spec.notes().stream().filter(note -> note != null && !note.isBlank()).forEach(lines::add);
        appendActions(lines, spec.correctiveActions());
        appendSample(lines, spec.primarySample(), "Sample Date");
        for (WorkbookSample followUpSample : spec.followUpSamples()) {
            appendSample(lines, followUpSample, "Retest Sample Date");
        }
        return String.join("\n", lines);
    }

    static String workbookComment(LocationDashboardCommentParser.ParsedComment parsedComment) {
        return workbookComment(new WorkbookCommentSpec(
            parsedComment.sampleLocation(),
            toWorkbookSample(parsedComment.primarySample()),
            parsedComment.followUpSamples().stream().map(LocationDashboardCommentFixtures::toWorkbookSample).toList(),
            parsedComment.correctiveActions().stream().map(LocationDashboardCommentFixtures::toWorkbookAction).toList(),
            parsedComment.notes()
        ));
    }

    private static WorkbookSample toWorkbookSample(LocationDashboardCommentParser.ParsedCommentSample sample) {
        if (sample == null) {
            return null;
        }
        return new WorkbookSample(
            sample.sampledOn(),
            sample.resultReceivedOn(),
            sample.resultRaw(),
            sample.resultValue(),
            sample.resultUnit(),
            sample.notes(),
            sample.correctiveActions().stream().map(LocationDashboardCommentFixtures::toWorkbookAction).toList(),
            null
        );
    }

    private static WorkbookAction toWorkbookAction(LocationDashboardCommentParser.ParsedCommentCorrectiveAction action) {
        return new WorkbookAction(action.actionDate(), action.text(), action.ticket(), action.notes());
    }

    private static void appendSample(List<String> lines, WorkbookSample sample, String dateLabel) {
        if (sample == null) {
            return;
        }
        addIfPresent(lines, prefixedLine("Sample Location", sample.sampleLocation()));
        addIfPresent(lines, prefixedLine(dateLabel, toCommentDate(sample.sampledOn())));
        addIfPresent(lines, prefixedLine("Result Date", toCommentDate(sample.resultReceivedOn())));
        addIfPresent(lines, prefixedLine("Result", sampleResultText(sample)));
        sample.notes().stream().filter(note -> note != null && !note.isBlank()).forEach(lines::add);
        appendActions(lines, sample.correctiveActions());
    }

    private static void appendActions(List<String> lines, List<WorkbookAction> actions) {
        if (actions == null) {
            return;
        }
        for (WorkbookAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.actionDate() != null && action.text() != null && !action.text().isBlank()) {
                lines.add("Action " + toCommentDate(action.actionDate()) + ": " + action.text());
            } else {
                addIfPresent(lines, prefixedLine("Action", action.text()));
            }
            addIfPresent(lines, prefixedLine("Ticket", action.ticket()));
            action.notes().stream().filter(note -> note != null && !note.isBlank()).forEach(lines::add);
        }
    }

    private static String sampleResultText(WorkbookSample sample) {
        if (sample == null) {
            return null;
        }
        if (sample.resultRaw() != null && !sample.resultRaw().isBlank()) {
            return sample.resultRaw();
        }
        if (sample.resultValue() == null) {
            return null;
        }
        return sample.resultValue().toPlainString()
            + (sample.resultUnit() == null || sample.resultUnit().isBlank() ? "" : " " + sample.resultUnit());
    }

    private static String prefixedLine(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value;
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    private static String toCommentDate(LocalDate date) {
        return date == null ? null : COMMENT_DATE_FORMAT.format(date);
    }

    public record WorkbookCommentSpec(
        String sampleLocation,
        WorkbookSample primarySample,
        List<WorkbookSample> followUpSamples,
        List<WorkbookAction> correctiveActions,
        List<String> notes
    ) {
        public WorkbookCommentSpec {
            followUpSamples = followUpSamples == null ? List.of() : List.copyOf(followUpSamples);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    public record WorkbookSample(
        LocalDate sampledOn,
        LocalDate resultReceivedOn,
        String resultRaw,
        BigDecimal resultValue,
        String resultUnit,
        List<String> notes,
        List<WorkbookAction> correctiveActions,
        String sampleLocation
    ) {
        public WorkbookSample {
            notes = notes == null ? List.of() : List.copyOf(notes);
            correctiveActions = correctiveActions == null ? List.of() : List.copyOf(correctiveActions);
        }
    }

    public record WorkbookAction(
        LocalDate actionDate,
        String text,
        String ticket,
        List<String> notes
    ) {
        public WorkbookAction {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }
}
