package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategy.CorrectiveActionDraft;

final class LocationDashboardCorrectiveActionDraftFactory {
    List<CorrectiveActionDraft> buildCorrectiveActions(List<LocationDashboardAnalyzedSample> analyzedSamples) {
        List<CorrectiveActionDraft> drafts = new ArrayList<>();
        for (LocationDashboardAnalyzedSample analyzedSample : analyzedSamples) {
            if (analyzedSample == null || analyzedSample.sample() == null) {
                continue;
            }
            drafts.addAll(buildCommentCorrectiveActionDrafts(analyzedSample.sample()));
            if (analyzedSample.sample() instanceof LocationDashboardWorksheetSample worksheetSample) {
                if (!analyzedSample.compliant()) {
                    buildSyntheticCorrectiveActionDraft(worksheetSample).ifPresent(drafts::add);
                }
            }
        }
        return deduplicate(drafts);
    }

    private Optional<CorrectiveActionDraft> buildSyntheticCorrectiveActionDraft(LocationDashboardWorksheetSample sample) {
        if (sample == null || sample.measurementName() == null) {
            return Optional.empty();
        }

        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            sample.measurementName(),
            sample.observedDate(),
            sample.sublocation(),
            sample.facilityName(),
            sample.resolvedBuilding(),
            sample.resolvedSystem(),
            sample.pointOfUse(),
            sample.basis(),
            null,
            List.of()
        );
        descriptionLines.add("");
        descriptionLines.add("Note: Imported from an out-of-spec worksheet sample without cell comment metadata.");

        return Optional.of(new CorrectiveActionDraft(
            sample.observedDate(),
            readableCorrectiveActionTitle(sample.measurementName(), sample.observedDate(), null),
            String.join("\n", descriptionLines),
            sample.facilityName(),
            sample.systemTypeName(),
            sample.measurementName()
        ));
    }

    private List<CorrectiveActionDraft> buildCommentCorrectiveActionDrafts(LocationDashboardImportedSample sample) {
        if (sample instanceof LocationDashboardWorksheetSample worksheetSample) {
            return buildWorksheetCommentCorrectiveActionDrafts(worksheetSample);
        }
        if (sample instanceof LocationDashboardCommentSample commentSample) {
            return buildCommentSampleCorrectiveActionDrafts(commentSample);
        }
        return List.of();
    }

    private List<CorrectiveActionDraft> buildWorksheetCommentCorrectiveActionDrafts(LocationDashboardWorksheetSample sample) {
        LocationDashboardCommentParser.ParsedComment parsedComment = sample.parsedComment();
        if (parsedComment == null) {
            return List.of();
        }

        List<CorrectiveActionDraft> drafts = new ArrayList<>();
        for (LocationDashboardCommentParser.ParsedCommentCorrectiveAction action : parsedComment.correctiveActions()) {
            buildCommentCorrectiveActionDraft(sample, action, "Comment").ifPresent(drafts::add);
        }

        LocationDashboardCommentParser.ParsedCommentSample primarySample = parsedComment.primarySample();
        if (primarySample != null
            && primarySample.sampledOn() != null
            && sample.observedDate() != null
            && primarySample.sampledOn().equals(sample.observedDate())) {
            for (LocationDashboardCommentParser.ParsedCommentCorrectiveAction action : primarySample.correctiveActions()) {
                buildCommentCorrectiveActionDraft(sample, action, "Primary Sample").ifPresent(drafts::add);
            }
        }
        return List.copyOf(drafts);
    }

    private List<CorrectiveActionDraft> buildCommentSampleCorrectiveActionDrafts(LocationDashboardCommentSample sample) {
        LocationDashboardCommentParser.ParsedCommentSample parsedSample = sample.parsedSample();
        if (parsedSample == null) {
            return List.of();
        }

        List<CorrectiveActionDraft> drafts = new ArrayList<>();
        if (sample.origin() == LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY
            && sample.parsedComment() != null) {
            for (LocationDashboardCommentParser.ParsedCommentCorrectiveAction action : sample.parsedComment().correctiveActions()) {
                buildCommentCorrectiveActionDraft(sample, action, "Comment").ifPresent(drafts::add);
            }
        }
        for (LocationDashboardCommentParser.ParsedCommentCorrectiveAction action : parsedSample.correctiveActions()) {
            buildCommentCorrectiveActionDraft(sample, action, sample.sampleLabel()).ifPresent(drafts::add);
        }
        return List.copyOf(drafts);
    }

    private Optional<CorrectiveActionDraft> buildCommentCorrectiveActionDraft(
        LocationDashboardImportedSample sample,
        LocationDashboardCommentParser.ParsedCommentCorrectiveAction action,
        String sourceLabel
    ) {
        if (sample == null
            || action == null
            || action.text() == null
            || action.text().isBlank()
            || sample.measurementName() == null) {
            return Optional.empty();
        }

        List<String> actionLines = new ArrayList<>();
        actionLines.add("Imported Corrective Action: " + action.text().strip());
        if (sourceLabel != null && !sourceLabel.isBlank()) {
            actionLines.add("Imported Corrective Action Source: " + sourceLabel.strip());
        }
        if (action.actionDate() != null) {
            actionLines.add("Imported Corrective Action Date: " + action.actionDate());
        }
        if (action.ticket() != null && !action.ticket().isBlank()) {
            actionLines.add("Imported Corrective Action Ticket: " + action.ticket().strip());
        }
        if (!action.notes().isEmpty()) {
            actionLines.add("Imported Corrective Action Notes:");
            action.notes().stream()
                .filter(note -> note != null && !note.isBlank())
                .map(note -> "- " + note.strip())
                .forEach(actionLines::add);
        }

        List<String> descriptionLines = buildCorrectiveActionDescriptionLines(
            sample.measurementName(),
            sample.observedDate(),
            sample.sublocation(),
            sample.facilityName(),
            sample.resolvedBuilding(),
            sample.resolvedSystem(),
            sample.pointOfUse(),
            sample.basis(),
            sample.sampleIdentity(),
            actionLines
        );

        return Optional.of(new CorrectiveActionDraft(
            sample.observedDate(),
            readableCorrectiveActionTitle(sample.measurementName(), sample.observedDate(), action.text()),
            String.join("\n", descriptionLines),
            sample.facilityName(),
            sample.systemTypeName(),
            sample.measurementName()
        ));
    }

    private List<String> buildCorrectiveActionDescriptionLines(
        String measurementName,
        LocalDate observedDate,
        LocationDashboardImportStrategyConfig.SublocationConfig sublocation,
        String facilityName,
        String resolvedBuilding,
        String systemName,
        String pointOfUse,
        String basis,
        String sampleIdentity,
        List<String> extraLines
    ) {
        List<String> descriptionLines = new ArrayList<>();
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine(measurementName)
        );
        addDescriptionLine(
            descriptionLines,
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(observedDate)
        );
        if (sublocation != null && sublocation.displayName() != null && !sublocation.displayName().isBlank()) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.sublocationLine(sublocation.displayName())
            );
        } else if (facilityName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.facilityLine(facilityName)
            );
        }
        if (resolvedBuilding != null && !resolvedBuilding.isBlank()) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.buildingLine(resolvedBuilding)
            );
        }
        if (systemName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.systemLine(systemName)
            );
        }
        if (pointOfUse != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.pointOfUseLine(pointOfUse)
            );
        }
        if (basis != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.basisLine(basis)
            );
        }
        if (sampleIdentity != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.sampleIdentityLine(sampleIdentity)
            );
        }
        if (extraLines != null && !extraLines.isEmpty()) {
            descriptionLines.add("");
            extraLines.stream()
                .filter(line -> line != null && !line.isBlank())
                .forEach(descriptionLines::add);
        }
        return descriptionLines;
    }

    private void addDescriptionLine(List<String> descriptionLines, String value) {
        if (value != null) {
            descriptionLines.add(value);
        }
    }

    private String readableCorrectiveActionTitle(String metricName, LocalDate observedDate, String suffix) {
        String normalizedMetricName = metricName == null || metricName.isBlank() ? "Comment" : metricName.strip();
        String normalizedDate = observedDate == null ? "undated" : observedDate.toString();
        String normalizedSuffix = suffix == null || suffix.isBlank() ? null : suffix.strip();
        return normalizedSuffix == null
            ? "CA: " + normalizedMetricName + " " + normalizedDate
            : "CA: " + normalizedMetricName + " " + normalizedDate + " " + normalizedSuffix;
    }

    private List<CorrectiveActionDraft> deduplicate(List<CorrectiveActionDraft> drafts) {
        Map<String, CorrectiveActionDraft> draftsByIdentity = new LinkedHashMap<>();
        for (CorrectiveActionDraft draft : drafts) {
            String identity = LocationDashboardCorrectiveActionMetadataSupport.identityKey(
                draft.title(),
                draft.description()
            );
            draftsByIdentity.putIfAbsent(identity, draft);
        }
        return List.copyOf(draftsByIdentity.values());
    }
}
