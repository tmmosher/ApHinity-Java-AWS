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
                null,
                "Critical SPD",
                "Critical SPD",
                "HPC",
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
                null,
                "Critical SPD",
                "Critical SPD",
                "HPC",
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
                List.of(correctiveAction)
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
                List.of()
            );

        assertEquals(2, historicalData.nonConformances().size());
    }

    private LocationDashboardImportStrategy.AnalyzedSamplePoint worksheetFailure(String measurementName) {
        return new LocationDashboardImportStrategy.AnalyzedSamplePoint(
            LocalDate.parse("2025-01-01"),
            "Irvine",
            null,
            "Critical SPD",
            "Critical SPD",
            measurementName,
            "POU 1",
            "Range",
            null,
            false,
            false,
            null,
            LocationDashboardImportStrategy.SampleOrigin.WORKSHEET
        );
    }
}
