package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.correctiveAction;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.workbookComment;
import static com.aphinity.client_analytics_core.api.core.services.location.dashboardimport.LocationDashboardCommentFixtures.sample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocationDashboardCommentParserTest {
    @Test
    void parseWorkbookCommentPromotesPrimarySampleAndPreservesSupplementalSamples() {
        LocationDashboardCommentParser parser = new LocationDashboardCommentParser(List.of(
            new LocationDashboardImportStrategyConfig.MeasurementUnitConfig(
                "CFU.mL",
                List.of("CFU.ml")
            )
        ));

        LocationDashboardCommentParser.ParsedComment parsed = parser.parse(workbookComment(
            new LocationDashboardCommentFixtures.WorkbookCommentSpec(
                "Cooling Tower Sample Port",
                sample(
                    LocalDate.parse("2025-08-01"),
                    LocalDate.parse("2025-08-05"),
                    "10 CFU.ml",
                    new BigDecimal("10"),
                    "CFU.ml",
                    List.of("Primary sample note"),
                    List.of(correctiveAction("Primary sample action"))
                ),
                List.of(sample(
                    LocalDate.parse("2025-08-15"),
                    LocalDate.parse("2025-08-20"),
                    "5 CFU.mL",
                    new BigDecimal("5"),
                    "CFU.mL",
                    List.of("Follow-up sample note"),
                    List.of(correctiveAction("Follow-up sample action"))
                )),
                List.of(correctiveAction("Root corrective action")),
                List.of("General note")
            )
        ));

        assertFalse(parsed.structured());
        assertEquals("Cooling Tower Sample Port", parsed.sampleLocation());
        assertEquals(LocalDate.parse("2025-08-01"), parsed.primarySample().sampledOn());
        assertEquals(LocalDate.parse("2025-08-05"), parsed.primarySample().resultReceivedOn());
        assertEquals("10", parsed.primarySample().resultRaw());
        assertEquals(new BigDecimal("10"), parsed.primarySample().resultValue());
        assertEquals("CFU.mL", parsed.primarySample().resultUnit());
        assertEquals("Primary sample note", parsed.primarySample().notes().getFirst());
        assertEquals("Primary sample action", parsed.primarySample().correctiveActions().getFirst().text());
        assertEquals(1, parsed.followUpSamples().size());
        assertEquals(LocalDate.parse("2025-08-15"), parsed.followUpSamples().getFirst().sampledOn());
        assertEquals(LocalDate.parse("2025-08-20"), parsed.followUpSamples().getFirst().resultReceivedOn());
        assertEquals("5", parsed.followUpSamples().getFirst().resultRaw());
        assertEquals(new BigDecimal("5"), parsed.followUpSamples().getFirst().resultValue());
        assertEquals("CFU.mL", parsed.followUpSamples().getFirst().resultUnit());
        assertEquals("Follow-up sample note", parsed.followUpSamples().getFirst().notes().getFirst());
        assertEquals(
            "Follow-up sample action",
            parsed.followUpSamples().getFirst().correctiveActions().getFirst().text()
        );
        assertEquals(1, parsed.correctiveActions().size());
        assertEquals("Root corrective action", parsed.correctiveActions().getFirst().text());
        assertEquals(1, parsed.notes().size());
        assertEquals("General note", parsed.notes().getFirst());
        assertEquals(2, parsed.allSamples().size());
    }
}
