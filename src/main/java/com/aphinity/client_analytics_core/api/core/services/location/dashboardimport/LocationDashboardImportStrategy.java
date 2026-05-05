package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.dashboard.MeasurementBound;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface LocationDashboardImportStrategy {
    String locationName();

    List<LocationDashboardImportStrategyConfig.GraphConfig> graphDefinitions();

    LocationDashboardImportComputation computeImport(
        LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook workbook,
        List<MeasurementBound> measurementBounds
    );

    record LocationDashboardImportComputation(
        List<ComputedGraphPayload> graphs,
        List<CorrectiveActionDraft> correctiveActions
    ) {
    }

    record ComputedGraphPayload(
        String graphId,
        List<Map<String, Object>> data
    ) {
    }

    record CorrectiveActionDraft(
        LocalDate eventDate,
        String title,
        String description
    ) {
    }
}
