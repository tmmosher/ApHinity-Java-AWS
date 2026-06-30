package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface LocationDashboardImportStrategy {
    String locationName();

    List<LocationDashboardImportStrategyConfig.GraphConfig> graphDefinitions();

    List<LocationDashboardImportStrategyConfig.DerivedGraphConfig> derivedGraphDefinitions();

    default List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> spreadsheetIdentityPattern() {
        return List.of();
    }

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
        String measurementName
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
        String buildingName,
        String systemName,
        String systemTypeName,
        String measurementName,
        String pointOfUse,
        String basis,
        String sampleIdentity,
        boolean compliant,
        boolean resolved,
        Long turnaroundDays,
        SampleOrigin origin
    ) {
        boolean nonConforming() {
            return !compliant;
        }
    }
}
