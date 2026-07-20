package com.aphinity.client_analytics_core.api.core.services;

import java.util.Locale;

/** File-name policy for supported Office Open XML spreadsheet containers. */
public final class SpreadsheetFileTypes {
    public static final String SUPPORTED_EXTENSIONS_DESCRIPTION = ".xlsx or .xlsm";

    private SpreadsheetFileTypes() {
    }

    public static boolean isSupportedOfficeOpenXmlSpreadsheet(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.strip().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".xlsx") || normalized.endsWith(".xlsm");
    }
}
