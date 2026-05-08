package com.aphinity.client_analytics_core.api.core.services.location.dashboardimport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDashboardCommentParserTest {
    @Test
    void parseStructuredCommentPreservesPrimaryAndSupplementalSamples() {
        LocationDashboardCommentParser parser = new LocationDashboardCommentParser();

        LocationDashboardCommentParser.ParsedComment parsed = parser.parse("""
            {
              "schema": "aphinity.location-dashboard.comment.v1",
              "sampleLocation": "Cooling Tower Sample Port",
              "primarySample": {
                "sampledOn": "2025-08-01",
                "resultReceivedOn": "2025-08-05",
                "resultRaw": "10 CFU.mL",
                "resultValue": 10,
                "resultUnit": "CFU.mL",
                "notes": ["Primary sample note"],
                "correctiveActions": [
                  {
                    "text": "Primary sample action"
                  }
                ]
              },
              "followUpSamples": [
                {
                  "sampledOn": "2025-08-15",
                  "resultReceivedOn": "2025-08-20",
                  "resultRaw": "5 CFU.mL",
                  "resultValue": 5,
                  "resultUnit": "CFU.mL",
                  "notes": ["Follow-up sample note"],
                  "correctiveActions": [
                    {
                      "text": "Follow-up sample action"
                    }
                  ]
                }
              ],
              "correctiveActions": [
                "Root corrective action"
              ],
              "notes": [
                "General note"
              ]
            }
            """);

        assertTrue(parsed.structured());
        assertEquals("Cooling Tower Sample Port", parsed.sampleLocation());
        assertEquals(LocalDate.parse("2025-08-01"), parsed.primarySample().sampledOn());
        assertEquals(LocalDate.parse("2025-08-05"), parsed.primarySample().resultReceivedOn());
        assertEquals(new BigDecimal("10"), parsed.primarySample().resultValue());
        assertEquals("CFU.mL", parsed.primarySample().resultUnit());
        assertEquals("Primary sample note", parsed.primarySample().notes().getFirst());
        assertEquals("Primary sample action", parsed.primarySample().correctiveActions().getFirst().text());
        assertEquals(1, parsed.followUpSamples().size());
        assertEquals(LocalDate.parse("2025-08-15"), parsed.followUpSamples().getFirst().sampledOn());
        assertEquals(LocalDate.parse("2025-08-20"), parsed.followUpSamples().getFirst().resultReceivedOn());
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
