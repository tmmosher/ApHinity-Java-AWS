package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardIdentityFixtures.identityValues;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardHistoricalDataAssemblerTest {
    @Test
    void usesSamplesAsIncidentSourceAndCorrectiveActionsAsResolutionAnnotations() {
        LocationDashboardHistoricalDataAssembler assembler = new LocationDashboardHistoricalDataAssembler(
            new LocationDashboardCorrectiveActionService(
                null,
                Clock.fixed(Instant.parse("2025-01-10T00:00:00Z"), ZoneOffset.UTC)
            )
        );
        ServiceEvent correctiveAction = new ServiceEvent();
        correctiveAction.setCorrectiveAction(true);
        correctiveAction.setEventDate(LocalDate.parse("2025-01-01"));
        correctiveAction.setEndEventDate(LocalDate.parse("2025-01-03"));
        correctiveAction.setEndEventTime(LocalTime.MIDNIGHT);
        correctiveAction.setStatus(ServiceEventStatus.COMPLETED);
        correctiveAction.setDescription(String.join("\n", List.of(
            LocationDashboardCorrectiveActionMetadataSupport.measurementLine("HPC"),
            LocationDashboardCorrectiveActionMetadataSupport.observedAtLine(LocalDate.parse("2025-01-01")),
            LocationDashboardCorrectiveActionMetadataSupport.sublocationLine("Irvine"),
            LocationDashboardCorrectiveActionMetadataSupport.systemLine("Critical SPD")
        )));
        LocationDashboardImportStrategy.AnalyzedSamplePoint worksheetFailure =
            new LocationDashboardImportStrategy.AnalyzedSamplePoint(
                LocalDate.parse("2025-01-01"),
                "Irvine",
                "Critical SPD",
                "HPC",
                identityValues("Irvine", null, "Critical SPD", null, null),
                null,
                null,
                null,
                false,
                false,
                null,
                LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
            );
        LocationDashboardImportStrategy.AnalyzedSamplePoint correctiveActionDraftSample =
            new LocationDashboardImportStrategy.AnalyzedSamplePoint(
                LocalDate.parse("2025-01-01"),
                "Irvine",
                "Critical SPD",
                "HPC",
                identityValues("Irvine", null, "Critical SPD", null, null),
                null,
                null,
                "divergent-persisted-sample-identity",
                false,
                true,
                2L,
                LocationDashboardImportStrategy.SampleOrigin.CORRECTIVE_ACTION_DRAFT
            );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            assembler.buildHistoricalDerivedData(
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(worksheetFailure, correctiveActionDraftSample),
                List.of(correctiveAction),
                List.of()
            );

        assertEquals(1, historicalData.nonConformances().size());
        assertTrue(historicalData.nonConformances().getFirst().resolved());
        assertEquals(2L, historicalData.nonConformances().getFirst().turnaroundDays());
    }

    @Test
    void keepsDistinctSameDayFailuresWithoutExplicitSampleIdentities() {
        LocationDashboardHistoricalDataAssembler assembler = new LocationDashboardHistoricalDataAssembler(
            new LocationDashboardCorrectiveActionService(
                null,
                Clock.fixed(Instant.parse("2025-01-10T00:00:00Z"), ZoneOffset.UTC)
            )
        );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            assembler.buildHistoricalDerivedData(
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(
                    worksheetFailure("HPC"),
                    worksheetFailure("HPC")
                ),
                List.of(),
                List.of()
            );

        assertEquals(2, historicalData.nonConformances().size());
    }

    @Test
    void buildsRawSampleIdentityFromDelimitedSampleIdentityBeforeLegacyFields() {
        LocationDashboardHistoricalDataAssembler assembler = new LocationDashboardHistoricalDataAssembler(
            new LocationDashboardCorrectiveActionService(
                null,
                Clock.fixed(Instant.parse("2025-01-10T00:00:00Z"), ZoneOffset.UTC)
            )
        );
        LocationDashboardImportStrategy.AnalyzedSamplePoint sample =
            new LocationDashboardImportStrategy.AnalyzedSamplePoint(
                LocalDate.parse("2025-01-01"),
                "Legacy Facility",
                "Legacy System Type",
                "HPC",
                identityValues("Newport", "Tower A", "Critical SPD", "Sink 1", "Routine"),
                null,
                null,
                "Newport|Tower A|Critical SPD|HPC|Sink 1|Routine",
                true,
                false,
                null,
                LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
            );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            assembler.buildHistoricalDerivedData(
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(sample),
                List.of(),
                List.of(
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("facility", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("building", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("system", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("pointOfUse", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("basis", List.of())
                )
            );

        LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample = historicalData.rawSamples().getFirst();
        assertEquals(Map.of(
            "facility", "Newport",
            "building", "Tower A",
            "system", "Critical SPD",
            "pointOfUse", "Sink 1",
            "basis", "Routine"
        ), rawSample.identityValues());
        assertEquals("newport|tower a|critical spd|sink 1|routine|hpc", rawSample.rowIdentifier());
    }

    @Test
    void buildsRawSampleIdentityFromRowScopedCommentSampleIdentities() {
        LocationDashboardHistoricalDataAssembler assembler = new LocationDashboardHistoricalDataAssembler(
            new LocationDashboardCorrectiveActionService(
                null,
                Clock.fixed(Instant.parse("2025-01-10T00:00:00Z"), ZoneOffset.UTC)
            )
        );
        LocationDashboardImportStrategy.AnalyzedSamplePoint sample =
            new LocationDashboardImportStrategy.AnalyzedSamplePoint(
                LocalDate.parse("2026-06-15"),
                "Newport",
                "Critical SPD",
                "HPC",
                identityValues("Newport", "Tower A", "Critical SPD", "Sink 1", "Routine"),
                "33 CFU.mL",
                null,
                "primary-sample|Newport|Tower A|Critical SPD|HPC|Sink 1|Routine|F5|2026-06-15|2026-06-19|33 CFU.mL",
                false,
                true,
                4L,
                LocationDashboardImportStrategy.SampleOrigin.COMMENT_PRIMARY
            );

        LocationDashboardDerivedGraphSupport.HistoricalDerivedData historicalData =
            assembler.buildHistoricalDerivedData(
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(sample),
                List.of(),
                List.of(
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("facility", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("building", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("system", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("pointOfUse", List.of()),
                    new LocationDashboardImportStrategyConfig.SpreadsheetIdentityColumn("basis", List.of())
                )
            );

        LocationDashboardDerivedGraphSupport.HistoricalRawSample rawSample = historicalData.rawSamples().getFirst();
        assertEquals(Map.of(
            "facility", "Newport",
            "building", "Tower A",
            "system", "Critical SPD",
            "pointOfUse", "Sink 1",
            "basis", "Routine"
        ), rawSample.identityValues());
        assertEquals("newport|tower a|critical spd|sink 1|routine|hpc", rawSample.rowIdentifier());
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint worksheetFailure(String measurementName) {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            LocalDate.parse("2025-01-01"),
            "Irvine",
            "Critical SPD",
            measurementName,
            identityValues("Irvine", null, "Critical SPD", "POU 1", "Range"),
            null,
            null,
            null,
            false,
            false,
            null,
            LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
        );
    }
}
