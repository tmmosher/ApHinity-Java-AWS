package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardImportStrategy.CorrectiveActionDraft;

final class LocationDashboardCorrectiveActionDraftFactory {
    // Comment-derived corrective actions are intentionally disconnected from sample analysis.
    // When reintroducing them, add that policy here rather than inside the sample import path.
    List<CorrectiveActionDraft> buildWorksheetCorrectiveActions(List<LocationDashboardAnalyzedSample> analyzedSamples) {
        List<CorrectiveActionDraft> drafts = new ArrayList<>();
        for (LocationDashboardAnalyzedSample analyzedSample : analyzedSamples) {
            if (!(analyzedSample.sample() instanceof LocationDashboardWorksheetSample worksheetSample) || analyzedSample.compliant()) {
                continue;
            }
            buildSyntheticCorrectiveActionDraft(worksheetSample).ifPresent(drafts::add);
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
            sample.systemTypeName(),
            sample.pointOfUse(),
            sample.basis(),
            null
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

    private List<String> buildCorrectiveActionDescriptionLines(
        String measurementName,
        LocalDate observedDate,
        LocationDashboardImportStrategyConfig.SublocationConfig sublocation,
        String facilityName,
        String resolvedBuilding,
        String systemTypeName,
        String pointOfUse,
        String basis,
        String sampleIdentity
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
        if (systemTypeName != null) {
            addDescriptionLine(
                descriptionLines,
                LocationDashboardCorrectiveActionMetadataSupport.systemLine(systemTypeName)
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
