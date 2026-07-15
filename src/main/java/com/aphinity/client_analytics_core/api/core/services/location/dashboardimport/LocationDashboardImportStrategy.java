package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Location-specific dashboard import rules used to convert a parsed workbook
 * into graph updates, observations, corrective action drafts, and analyzed
 * historical samples.
 */
public interface LocationDashboardImportStrategy {
    /**
     * Returns the canonical location name this strategy supports.
     *
     * @return configured location name
     */
    String locationName();

    /**
     * Returns persisted graph definitions that should be matched for imports.
     *
     * @return configured graph definitions
     */
    List<LocationDashboardImportStrategyConfig.GraphConfig> graphDefinitions();

    /**
     * Returns derived graph definitions that are rebuilt from historical data.
     *
     * @return configured derived graph definitions
     */
    List<LocationDashboardImportStrategyConfig.DerivedGraphConfig> derivedGraphDefinitions();

    /**
     * Returns the spreadsheet identity columns required by this strategy.
     *
     * @return identity column pattern
     */
    default List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> spreadsheetIdentityPattern() {
        return List.of();
    }

    /**
     * Computes imported graph payloads and historical records from a parsed workbook.
     *
     * @param workbook parsed dashboard workbook
     * @param measurementBounds configured compliance bounds
     * @return complete import computation
     */
    LocationDashboardImportComputation computeImport(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        List<MeasurementBound> measurementBounds
    );

    record LocationDashboardImportComputation(
        List<ComputedGraphPayload> graphs,
        List<ImportedObservation> observations,
        List<CorrectiveActionDraft> correctiveActions,
        List<AnalyzedSamplePoint> analyzedSamples
    ) {
    }

    record ComputedGraphPayload(
        String graphId,
        List<Map<String, Object>> data
    ) {
    }

    record ImportedObservation(
        LocalDate observedDate,
        String facilityName,
        String systemTypeName,
        String measurementName,
        boolean compliant
    ) {
    }

    record CorrectiveActionDraft(
        LocalDate observedDate,
        String title,
        String description,
        String facilityName,
        String systemTypeName,
        String measurementName,
        boolean resolved,
        LocalDate resolvedDate
    ) {
    }

    enum SampleOrigin {
        WORKSHEET,
        COMMENT_PRIMARY,
        COMMENT_SUPPLEMENTAL,
        CORRECTIVE_ACTION_DRAFT
    }

    record AnalyzedSamplePoint(
        LocalDate observedDate,
        String facilityName,
        String systemTypeName,
        String measurementName,
        Map<String, String> identityValues,
        String rawValue,
        String units,
        String sampleIdentity,
        boolean compliant,
        boolean resolved,
        Long turnaroundDays,
        SampleOrigin origin
    ) {
        public AnalyzedSamplePoint {
            identityValues = identityValues == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(identityValues));
        }

        boolean nonConforming() {
            return !compliant;
        }
    }
}
