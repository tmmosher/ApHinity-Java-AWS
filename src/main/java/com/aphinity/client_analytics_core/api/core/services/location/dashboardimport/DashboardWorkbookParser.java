package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Application port for converting an uploaded workbook into import facts. */
public interface DashboardWorkbookParser {
    LocationDashboardSpreadsheetParser.ParsedDashboardWorkbook parse(
        MultipartFile file,
        List<LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn> identityPattern
    );
}
