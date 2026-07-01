package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.auth.AuthSession;
import com.aphinity.client_analytics_core.api.core.entities.dashboard.Graph;
import com.aphinity.client_analytics_core.api.core.entities.location.Location;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEvent;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventResponsibility;
import com.aphinity.client_analytics_core.api.core.entities.servicecalendar.ServiceEventStatus;
import com.aphinity.client_analytics_core.api.core.plotly.GraphPayloadMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String PASSWORD = "ValidPass12!";

    @Test
    void locationsRejectMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/core/locations"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void updateLocationGraphsRejectsMissingAuthentication() throws Exception {
        Location location = createLocation("Unauthenticated graph updates");
        Graph graph = createGraph("Graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void locationsReturnsMembershipScopedDataForClientUser() throws Exception {
        AppUser user = createUser("client-locations@example.com", PASSWORD, true, "client");
        Location location = createLocation("Austin");
        addMembership(location, user);

        AuthCookies authCookies = loginAndCaptureCookies("client-locations@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Austin"));
    }

    @Test
    void locationEventsReturnsMembershipScopedDataForClientUser() throws Exception {
        AppUser user = createUser("client-events@example.com", PASSWORD, true, "client");
        Location location = createLocation("Mesa");
        addMembership(location, user);
        createServiceEvent(
            location,
            "Archived inspection",
            ServiceEventResponsibility.CLIENT,
            LocalDate.parse("2025-12-15"),
            LocalTime.parse("10:00:00"),
            LocalDate.parse("2025-12-15"),
            LocalTime.parse("11:00:00"),
            "Outside the requested three-month window",
            ServiceEventStatus.COMPLETED
        );
        createServiceEvent(
            location,
            "Water meter inspection",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-03"),
            LocalTime.parse("09:30:00"),
            LocalDate.parse("2026-04-04"),
            LocalTime.parse("11:15:00"),
            "Inspect the primary water meter",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("client-events@example.com", PASSWORD);

        mockMvc.perform(
                get("/api/core/locations/{locationId}/events", location.getId())
                    .param("month", "2026-04")
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Water meter inspection"))
            .andExpect(jsonPath("$[0].responsibility").value("partner"))
            .andExpect(jsonPath("$[0].date").value("2026-04-03"))
            .andExpect(jsonPath("$[0].time").value("09:30:00"))
            .andExpect(jsonPath("$[0].endDate").value("2026-04-04"))
            .andExpect(jsonPath("$[0].endTime").value("11:15:00"))
            .andExpect(jsonPath("$[0].status").value("upcoming"));
    }

    @Test
    void downloadLocationEventTemplateRejectsMissingAuthentication() throws Exception {
        Location location = createLocation("Unauthenticated template location");

        mockMvc.perform(get("/api/core/locations/{locationId}/events/template", location.getId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void downloadLocationEventTemplateReturnsAttachmentForAccessibleLocation() throws Exception {
        AppUser user = createUser("client-events-template@example.com", PASSWORD, true, "client");
        Location location = createLocation("Scottsdale");
        addMembership(location, user);

        AuthCookies authCookies = loginAndCaptureCookies("client-events-template@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                get("/api/core/locations/{locationId}/events/template", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentType())
            .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
            .contains("attachment")
            .contains("service_calendar_template.xlsx");
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void uploadLocationEventCalendarImportsSpreadsheetRows() throws Exception {
        createUser("partner-events-upload@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Flagstaff");
        AuthCookies authCookies = loginAndCaptureCookies("partner-events-upload@example.com", PASSWORD);

        byte[] spreadsheet = createServiceCalendarSpreadsheet(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Imported visit", "Spreadsheet upload", "2026-04-15", "2026-04-15", "09:30", "10:45", "False", "Partner")
        );

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/events/calendar-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "service_calendar_upload.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.importedCount").value(1));

        List<ServiceEvent> persistedEvents = serviceEventRepository.findAll();
        assertEquals(1, persistedEvents.size());
        assertEquals("Imported visit", persistedEvents.getFirst().getTitle());
        assertEquals(ServiceEventResponsibility.PARTNER, persistedEvents.getFirst().getResponsibility());
    }

    @Test
    void uploadLocationEventCalendarReturnsRowSpecificErrors() throws Exception {
        createUser("partner-events-upload-invalid@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Prescott");
        AuthCookies authCookies = loginAndCaptureCookies("partner-events-upload-invalid@example.com", PASSWORD);

        byte[] spreadsheet = createServiceCalendarSpreadsheet(
            List.of("Title", "Description", "Start Date", "End Date", "Start Time", "End Time", "All Day", "Responsibility"),
            List.of("Imported visit", "", "2026-04-15", "2026-04-15", "09:30", "10:45", "False", "Vendor")
        );

        mockMvc.perform(
                multipart("/api/core/locations/{locationId}/events/calendar-upload", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "service_calendar_upload.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        spreadsheet
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("service_calendar_row_invalid"))
            .andExpect(jsonPath("$.message").value("Row 2: Responsibility must be Client or Partner."));
    }

    @Test
    void createLocationAllowsAdmins() throws Exception {
        createUser("admin-locations@example.com", PASSWORD, true, "admin");
        AuthCookies authCookies = loginAndCaptureCookies("admin-locations@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Phoenix"}
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Phoenix"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String createdUpdatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        assertTrue(locationRepository.findByName("Phoenix").isPresent());
        mockMvc.perform(
                get("/api/core/locations/{locationId}", locationRepository.findByName("Phoenix").orElseThrow().getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value(createdUpdatedAt));
    }

    @Test
    void createLocationRejectsPartners() throws Exception {
        createUser("partner-locations@example.com", PASSWORD, true, "partner");
        AuthCookies authCookies = loginAndCaptureCookies("partner-locations@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Phoenix"}
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void createLocationEventAllowsPartnerAndPersistsTrimmedFields() throws Exception {
        createUser("partner-events@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Gilbert");
        location.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        locationRepository.saveAndFlush(location);

        AuthCookies authCookies = loginAndCaptureCookies("partner-events@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "  Pump station visit  ",
                          "responsibility": "partner",
                          "date": "2026-04-10",
                          "time": "08:45:00",
                          "endDate": "2026-04-12",
                          "endTime": "17:15:00",
                          "description": "  Check the north pump station  ",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Pump station visit"))
            .andExpect(jsonPath("$.responsibility").value("partner"))
            .andExpect(jsonPath("$.date").value("2026-04-10"))
            .andExpect(jsonPath("$.time").value("08:45:00"))
            .andExpect(jsonPath("$.endDate").value("2026-04-12"))
            .andExpect(jsonPath("$.endTime").value("17:15:00"))
            .andExpect(jsonPath("$.description").value("Check the north pump station"))
            .andExpect(jsonPath("$.status").value("upcoming"))
            .andReturn();

        ServiceEvent persisted = serviceEventRepository.findAll().getFirst();
        assertEquals("Pump station visit", persisted.getTitle());
        assertEquals("Check the north pump station", persisted.getDescription());
        assertEquals(LocalDate.parse("2026-04-12"), persisted.getEndEventDate());
        assertEquals(LocalTime.parse("17:15:00"), persisted.getEndEventTime());

        Location updatedLocation = locationRepository.findById(location.getId()).orElseThrow();
        assertTrue(updatedLocation.getUpdatedAt().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(result.getResponse().getContentAsString()).contains("Pump station visit");

        String createdUpdatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
                    .param("month", "2026-04")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(createdUpdatedAt));
    }

    @Test
    void createLocationEventRejectsEndBeforeStart() throws Exception {
        createUser("partner-events-invalid-range@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Glendale");

        AuthCookies authCookies = loginAndCaptureCookies("partner-events-invalid-range@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Invalid event range",
                          "responsibility": "partner",
                          "date": "2026-04-10",
                          "time": "08:45:00",
                          "endDate": "2026-04-09",
                          "endTime": "17:15:00",
                          "description": "Should fail validation",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("event_range_invalid"))
            .andExpect(jsonPath("$.message").value("Event end must be on or after the start date and time"));
    }

    @Test
    void createLocationEventAllowsTitleLongerThanFortyTwoCharacters() throws Exception {
        createUser("partner-events-invalid-title@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Chandler");
        String longTitle = "1234567890123456789012345678901234567890123";

        AuthCookies authCookies = loginAndCaptureCookies("partner-events-invalid-title@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "%s",
                          "responsibility": "partner",
                          "date": "2026-04-10",
                          "time": "08:45:00",
                          "endDate": "2026-04-10",
                          "endTime": "17:15:00",
                          "description": "Should preserve the full title",
                          "status": "upcoming"
                        }
                        """.formatted(longTitle))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value(longTitle))
            .andExpect(jsonPath("$.description").value("Should preserve the full title"));
    }

    @Test
    void createLocationEventAllowsClientWhenResponsibilityIsClient() throws Exception {
        AppUser client = createUser("client-events-write@example.com", PASSWORD, true, "client");
        Location location = createLocation("Tempe");
        addMembership(location, client);

        AuthCookies authCookies = loginAndCaptureCookies("client-events-write@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Client can create this",
                          "responsibility": "client",
                          "date": "2026-04-12",
                          "time": "10:15:00",
                          "endDate": "2026-04-12",
                          "endTime": "12:00:00",
                          "description": "Client-owned schedule item",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Client can create this"))
            .andExpect(jsonPath("$.responsibility").value("client"))
            .andExpect(jsonPath("$.endDate").value("2026-04-12"))
            .andExpect(jsonPath("$.endTime").value("12:00:00"))
            .andExpect(jsonPath("$.description").value("Client-owned schedule item"));

        ServiceEvent persisted = serviceEventRepository.findAll().getFirst();
        assertEquals("Client can create this", persisted.getTitle());
        assertEquals(ServiceEventResponsibility.CLIENT, persisted.getResponsibility());
        assertEquals(LocalDate.parse("2026-04-12"), persisted.getEndEventDate());
        assertEquals(LocalTime.parse("12:00:00"), persisted.getEndEventTime());
    }

    @Test
    void createLocationEventRejectsClientWhenResponsibilityIsPartner() throws Exception {
        AppUser client = createUser("client-events-write-partner@example.com", PASSWORD, true, "client");
        Location location = createLocation("Tempe West");
        addMembership(location, client);

        AuthCookies authCookies = loginAndCaptureCookies("client-events-write-partner@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Client should not create this",
                          "responsibility": "partner",
                          "date": "2026-04-12",
                          "time": "10:15:00",
                          "endDate": "2026-04-12",
                          "endTime": "12:00:00",
                          "description": "Attempted unauthorized mutation",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void createCorrectiveActionRejectsClientWhenSourceEventIsPartnerOwned() throws Exception {
        AppUser client = createUser("client-corrective-action-partner@example.com", PASSWORD, true, "client");
        Location location = createLocation("Scottsdale");
        addMembership(location, client);
        ServiceEvent sourceEvent = createServiceEvent(
            location,
            "Partner inspection",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-14"),
            LocalTime.parse("09:00:00"),
            "Partner-owned event",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("client-corrective-action-partner@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/events/{sourceEventId}/corrective-actions", location.getId(), sourceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Client corrective action",
                          "responsibility": "client",
                          "date": "2026-04-15",
                          "time": "10:15:00",
                          "endDate": "2026-04-15",
                          "endTime": "12:00:00",
                          "description": "Attempted unauthorized corrective action",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));

        assertEquals(1L, serviceEventRepository.count());
    }

    @Test
    void createCorrectiveActionReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-corrective-action-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Palm Springs");
        location.setWorkOrderEmail("workorders@example.com");
        locationRepository.saveAndFlush(location);
        ServiceEvent sourceEvent = createServiceEvent(
            location,
            "Source event",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-03-14"),
            LocalTime.parse("09:00:00"),
            LocalDate.parse("2026-03-14"),
            LocalTime.parse("10:00:00"),
            "Source description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("partner-corrective-action-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations/{locationId}/events/{sourceEventId}/corrective-actions", location.getId(), sourceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Corrective action",
                          "responsibility": "partner",
                          "date": "2026-04-15",
                          "time": "10:15:00",
                          "endDate": "2026-04-15",
                          "endTime": "12:00:00",
                          "description": "Corrective action description",
                          "status": "upcoming"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
                    .param("month", "2026-04")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title").value(hasItem("Corrective action")))
            .andExpect(jsonPath("$[?(@.title == 'Corrective action')].updatedAt").value(hasItem(updatedAt)));
    }

    @Test
    void updateLocationEventAllowsPartnerAndPersistsChanges() throws Exception {
        createUser("partner-events-update@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Peoria");
        location.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        locationRepository.saveAndFlush(location);
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Original event",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-05"),
            LocalTime.parse("07:30:00"),
            "Original description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("partner-events-update@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Updated event",
                          "responsibility": "client",
                          "date": "2026-04-06",
                          "time": "11:00:00",
                          "endDate": "2026-04-08",
                          "endTime": "16:30:00",
                          "description": "Updated description",
                          "status": "current"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated event"))
            .andExpect(jsonPath("$.responsibility").value("client"))
            .andExpect(jsonPath("$.date").value("2026-04-06"))
            .andExpect(jsonPath("$.time").value("11:00:00"))
            .andExpect(jsonPath("$.endDate").value("2026-04-08"))
            .andExpect(jsonPath("$.endTime").value("16:30:00"))
            .andExpect(jsonPath("$.description").value("Updated description"))
            .andExpect(jsonPath("$.status").value("current"));

        ServiceEvent persisted = serviceEventRepository.findById(serviceEvent.getId()).orElseThrow();
        assertEquals("Updated event", persisted.getTitle());
        assertEquals(ServiceEventResponsibility.CLIENT, persisted.getResponsibility());
        assertEquals(ServiceEventStatus.CURRENT, persisted.getStatus());
        assertEquals(LocalDate.parse("2026-04-08"), persisted.getEndEventDate());
        assertEquals(LocalTime.parse("16:30:00"), persisted.getEndEventTime());

        Location updatedLocation = locationRepository.findById(location.getId()).orElseThrow();
        assertTrue(updatedLocation.getUpdatedAt().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void updateLocationEventAllowsClientWhenEventRemainsClientResponsibility() throws Exception {
        AppUser client = createUser("client-events-update@example.com", PASSWORD, true, "client");
        Location location = createLocation("Chandler");
        addMembership(location, client);
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Client event",
            ServiceEventResponsibility.CLIENT,
            LocalDate.parse("2026-04-09"),
            LocalTime.parse("13:00:00"),
            "Original client description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("client-events-update@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Client event updated",
                          "responsibility": "client",
                          "date": "2026-04-10",
                          "time": "14:30:00",
                          "endDate": "2026-04-10",
                          "endTime": "15:45:00",
                          "description": "Updated client description",
                          "status": "current"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Client event updated"))
            .andExpect(jsonPath("$.responsibility").value("client"))
            .andExpect(jsonPath("$.date").value("2026-04-10"))
            .andExpect(jsonPath("$.time").value("14:30:00"))
            .andExpect(jsonPath("$.endDate").value("2026-04-10"))
            .andExpect(jsonPath("$.endTime").value("15:45:00"))
            .andExpect(jsonPath("$.description").value("Updated client description"))
            .andExpect(jsonPath("$.status").value("current"));

        ServiceEvent persisted = serviceEventRepository.findById(serviceEvent.getId()).orElseThrow();
        assertEquals("Client event updated", persisted.getTitle());
        assertEquals(ServiceEventResponsibility.CLIENT, persisted.getResponsibility());
        assertEquals(ServiceEventStatus.CURRENT, persisted.getStatus());
        assertEquals(LocalDate.parse("2026-04-10"), persisted.getEndEventDate());
        assertEquals(LocalTime.parse("15:45:00"), persisted.getEndEventTime());
    }

    @Test
    void updateLocationEventRejectsClientWhenChangingResponsibilityToPartner() throws Exception {
        AppUser client = createUser("client-events-update-partner@example.com", PASSWORD, true, "client");
        Location location = createLocation("Chandler West");
        addMembership(location, client);
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Client event",
            ServiceEventResponsibility.CLIENT,
            LocalDate.parse("2026-04-09"),
            LocalTime.parse("13:00:00"),
            "Original client description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("client-events-update-partner@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Partner takeover",
                          "responsibility": "partner",
                          "date": "2026-04-10",
                          "time": "14:30:00",
                          "endDate": "2026-04-10",
                          "endTime": "15:45:00",
                          "description": "Updated client description",
                          "status": "current"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void updateLocationEventRejectsClientForPartnerOwnedEvent() throws Exception {
        AppUser client = createUser("client-events-update-owned@example.com", PASSWORD, true, "client");
        Location location = createLocation("Chandler North");
        addMembership(location, client);
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Partner event",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-09"),
            LocalTime.parse("13:00:00"),
            "Original partner description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("client-events-update-owned@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Client attempt",
                          "responsibility": "client",
                          "date": "2026-04-10",
                          "time": "14:30:00",
                          "endDate": "2026-04-10",
                          "endTime": "15:45:00",
                          "description": "Updated client description",
                          "status": "current"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void deleteLocationEventRemovesPersistedEvent() throws Exception {
        createUser("partner-events-delete@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Scottsdale");
        location.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        locationRepository.saveAndFlush(location);
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Delete event",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-08"),
            LocalTime.parse("12:15:00"),
            "Delete me",
            ServiceEventStatus.OVERDUE
        );

        AuthCookies authCookies = loginAndCaptureCookies("partner-events-delete@example.com", PASSWORD);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isNoContent());

        assertTrue(serviceEventRepository.findById(serviceEvent.getId()).isEmpty());
        Location updatedLocation = locationRepository.findById(location.getId()).orElseThrow();
        assertTrue(updatedLocation.getUpdatedAt().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void locationsAutoRefreshesExpiredAccessTokenWhenRefreshTokenIsValid() throws Exception {
        AppUser user = createUser("refresh-core@example.com", PASSWORD, true, "client");
        Location location = createLocation("Denver");
        addMembership(location, user);

        AuthCookies loginCookies = loginAndCaptureCookies("refresh-core@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(user, initialSession.getId());

        MvcResult result = mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(expiredAccessToken, loginCookies.refreshToken()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Denver"))
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
        assertEquals(2L, authSessionRepository.count());
    }

    @Test
    void locationsClearsCookiesWhenExpiredAccessTokenHasInvalidRefreshToken() throws Exception {
        AppUser user = createUser("invalid-refresh@example.com", PASSWORD, true, "client");
        String expiredAccessToken = createExpiredAccessToken(user, 999L);

        MvcResult result = mockMvc.perform(
                get("/api/core/locations")
                    .cookie(authCookies(expiredAccessToken, "bogus-refresh-token"))
            )
            .andExpect(status().isUnauthorized())
            .andReturn();

        Map<String, String> clearedCookies = readSetCookies(result);
        assertThat(clearedCookies).containsEntry(AuthCookieNames.ACCESS_COOKIE_NAME, "");
        assertThat(clearedCookies).containsEntry(AuthCookieNames.REFRESH_COOKIE_NAME, "");
    }

    @Test
    void createLocationInviteAutoRefreshesExpiredAccessTokenWhenRefreshTokenIsValid() throws Exception {
        AppUser partner = createUser("partner-invite-refresh@example.com", PASSWORD, true, "partner");
        AppUser invited = createUser("client-invite-refresh@example.com", PASSWORD, true, "client");
        Location location = createLocation("Irvine");

        AuthCookies loginCookies = loginAndCaptureCookies("partner-invite-refresh@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(partner, initialSession.getId());

        MvcResult result = mockMvc.perform(
                post("/api/core/location-invites")
                    .cookie(authCookies(expiredAccessToken, loginCookies.refreshToken()))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"locationId": %d, "invitedEmail": "%s"}
                        """.formatted(location.getId(), invited.getEmail()))
            )
            .andExpect(status().isOk())
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
    }

    @Test
    void concurrentExpiredAccessRequestsShareSingleRefreshExecution() throws Exception {
        int requestCount = 6;
        AppUser user = createUser("race-core@example.com", PASSWORD, true, "client");
        Location location = createLocation("Phoenix");
        addMembership(location, user);
        AuthCookies loginCookies = loginAndCaptureCookies("race-core@example.com", PASSWORD);

        AuthSession originalSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(user, originalSession.getId());

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(concurrentLocationRequestTask(
                    ready,
                    start,
                    expiredAccessToken,
                    loginCookies.refreshToken()
                )));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<MvcResult> future : futures) {
                MvcResult response = future.get(10, TimeUnit.SECONDS);
                assertEquals(HttpStatus.OK.value(), response.getResponse().getStatus());
                assertThat(response.getResponse().getContentAsString()).contains("Phoenix");
                Map<String, String> setCookies = readSetCookies(response);
                assertThat(setCookies).containsKey(AuthCookieNames.ACCESS_COOKIE_NAME);
                assertThat(setCookies).containsKey(AuthCookieNames.REFRESH_COOKIE_NAME);
            }
        } finally {
            executor.shutdownNow();
        }

        List<AuthSession> sessions = authSessionRepository.findAll();
        long activeSessions = sessions.stream().filter(session -> session.getRevokedAt() == null).count();
        assertEquals(2L, sessions.size());
        assertEquals(1L, activeSessions);
    }

    @Test
    void deleteMembershipRemovesTargetUserMembership() throws Exception {
        AppUser partner = createUser("partner@example.com", PASSWORD, true, "partner");
        AppUser target = createUser("target@example.com", PASSWORD, true, "client");
        Location location = createLocation("Chicago");
        addMembership(location, target);

        AuthCookies authCookies = loginAndCaptureCookies("partner@example.com", PASSWORD);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/memberships/{userId}", location.getId(), target.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isNoContent());

        assertTrue(locationUserRepository.findByIdLocationIdAndIdUserId(location.getId(), target.getId()).isEmpty());
    }

    @Test
    void deleteMembershipRejectsMismatchedCsrfToken() throws Exception {
        AppUser partner = createUser("partner-delete-stale-csrf@example.com", PASSWORD, true, "partner");
        AppUser target = createUser("target-delete-stale-csrf@example.com", PASSWORD, true, "client");
        Location location = createLocation("Orange");
        addMembership(location, target);

        AuthCookies authCookies = loginAndCaptureCookies("partner-delete-stale-csrf@example.com", PASSWORD);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/memberships/{userId}", location.getId(), target.getId())
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    void deleteMembershipSucceedsAfterRefreshingCsrfToken() throws Exception {
        AppUser partner = createUser("partner-delete-refresh-csrf@example.com", PASSWORD, true, "partner");
        AppUser target = createUser("target-delete-refresh-csrf@example.com", PASSWORD, true, "client");
        Location location = createLocation("Yorba Linda");
        addMembership(location, target);

        AuthCookies authCookies = loginAndCaptureCookies("partner-delete-refresh-csrf@example.com", PASSWORD);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/memberships/{userId}", location.getId(), target.getId())
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));

        assertTrue(locationUserRepository.findByIdLocationIdAndIdUserId(location.getId(), target.getId()).isPresent());

        String csrfToken = fetchCoreCsrfToken(authCookies);

        mockMvc.perform(
                delete("/api/core/locations/{locationId}/memberships/{userId}", location.getId(), target.getId())
                    .cookie(authCookiesWithCsrf(authCookies, csrfToken))
                    .header("X-XSRF-TOKEN", csrfToken)
            )
            .andExpect(status().isNoContent());

        assertTrue(locationUserRepository.findByIdLocationIdAndIdUserId(location.getId(), target.getId()).isEmpty());
    }

    @Test
    void updateLocationGraphsAllowsPartnerAndMutatesDataAndLayout() throws Exception {
        AppUser partner = createUser("partner-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Santa Ana");
        location.setUpdatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        locationRepository.saveAndFlush(location);
        Graph graph = createGraph("Water quality", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        graph.setLayout(Map.of("title", "Original layout"));
        graphRepository.save(graph);
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}],
                              "layout": {"title": "Updated by backend"}
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(graph.getId()))
            .andExpect(jsonPath("$[0].data[0].orientation").value("h"))
            .andExpect(jsonPath("$[0].data[0].x[0]").value(9))
            .andExpect(jsonPath("$[0].layout.title").value("Updated by backend"));

        Location updatedLocation = locationRepository.findById(location.getId()).orElseThrow();
        assertTrue(updatedLocation.getUpdatedAt().isAfter(java.time.Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void updateLocationGraphNameAllowsPartnerAndPersistsTrimmedName() throws Exception {
        createUser("partner-graph-rename@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Casa Grande");
        location.setUpdatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        locationRepository.saveAndFlush(location);
        Graph graph = createGraph("Original graph title", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graph-rename@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs/{graphId}/name", location.getId(), graph.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"  Updated graph title  "}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graphId").value(graph.getId()))
            .andExpect(jsonPath("$.name").value("Updated graph title"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Graph persisted = graphRepository.findById(graph.getId()).orElseThrow();
        assertEquals("Updated graph title", persisted.getName());
        Location updatedLocation = locationRepository.findById(location.getId()).orElseThrow();
        assertTrue(updatedLocation.getUpdatedAt().isAfter(java.time.Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void updateLocationGraphsAcceptsExpectedUpdatedAtReturnedFromRenameEndpoint() throws Exception {
        createUser("partner-graph-rename-followup@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Prescott");
        Graph graph = createGraph("Rename then update", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graph-rename-followup@example.com", PASSWORD);

        MvcResult renameResult = mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs/{graphId}/name", location.getId(), graph.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Renamed graph"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graphId").value(graph.getId()))
            .andExpect(jsonPath("$.name").value("Renamed graph"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String renamedUpdatedAt = extractJsonStringField(renameResult.getResponse().getContentAsString(), "updatedAt");

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "expectedUpdatedAt": "%s",
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId(), renamedUpdatedAt))
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(graph.getId()))
            .andExpect(jsonPath("$[0].name").value("Renamed graph"))
            .andExpect(jsonPath("$[0].data[0].orientation").value("h"))
            .andExpect(jsonPath("$[0].data[0].x[0]").value(9));
    }

    @Test
    void updateLocationGraphNameRejectsMismatchedCsrfToken() throws Exception {
        createUser("partner-graph-rename-csrf@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Surprise");
        Graph graph = createGraph("CSRF rename graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graph-rename-csrf@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs/{graphId}/name", location.getId(), graph.getId())
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Updated graph title"}
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    void updateLocationGraphNameRejectsClientUsers() throws Exception {
        createUser("client-graph-rename@example.com", PASSWORD, true, "client");
        Location location = createLocation("Goodyear");
        Graph graph = createGraph("Client rename graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("client-graph-rename@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs/{graphId}/name", location.getId(), graph.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Updated graph title"}
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));

        Graph persisted = graphRepository.findById(graph.getId()).orElseThrow();
        assertEquals("Client rename graph", persisted.getName());
    }

    @Test
    void updateLocationNameReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-location-update-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Apache Junction");
        AuthCookies authCookies = loginAndCaptureCookies("partner-location-update-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"name":"Apache Junction Updated"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Apache Junction Updated"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value(updatedAt));
    }

    @Test
    void updateLocationWorkOrderEmailReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-location-email-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Casa Grande");
        AuthCookies authCookies = loginAndCaptureCookies("partner-location-email-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}/work-order-email", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"workOrderEmail":"dispatch@example.com"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workOrderEmail").value("dispatch@example.com"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value(updatedAt));
    }

    @Test
    void updateLocationThumbnailReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-location-thumbnail-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Chandler");
        AuthCookies authCookies = loginAndCaptureCookies("partner-location-thumbnail-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                multipart("/api/core/locations/{locationId}/thumbnail", location.getId())
                    .file(new MockMultipartFile(
                        "file",
                        "thumbnail.png",
                        "image/png",
                        createPngThumbnail()
                    ))
                    .contentType("multipart/form-data")
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thumbnailAvailable").value(true))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value(updatedAt))
            .andExpect(jsonPath("$.thumbnailAvailable").value(true));
    }

    @Test
    void createLocationGraphReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-graph-create-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Fountain Hills");
        AuthCookies authCookies = loginAndCaptureCookies("partner-graph-create-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "createNewSection": true,
                          "graphType": "bar"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(updatedAt));
    }

    @Test
    void updateLocationGraphsAcceptsDataOnlyPayloadFromFrontend() throws Exception {
        AppUser partner = createUser("partner-graphs-minimal@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Huntington Beach");
        Graph graph = createGraph("Data-only graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-minimal@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [6, 6, 6]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent());

        Graph persisted = reloadGraph(graph.getId());
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(persisted.getData());
        assertEquals("h", traces.getFirst().get("orientation"));
        assertEquals(List.of(6L, 6L, 6L), traces.getFirst().get("x"));
    }

    @Test
    void updateLocationGraphsPersistsExplicitVerticalBarOrientation() throws Exception {
        AppUser partner = createUser("partner-graphs-vertical@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Long Beach");
        Graph graph = createGraph("Vertical graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-vertical@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{
                                "type": "bar",
                                "orientation": "v",
                                "x": ["Jan", "Feb"],
                                "y": [6, 8]
                              }]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(graph.getId()))
            .andExpect(jsonPath("$[0].data[0].orientation").value("v"))
            .andExpect(jsonPath("$[0].data[0].x[0]").value("Jan"))
            .andExpect(jsonPath("$[0].data[0].y[0]").value(6));

        Graph persisted = reloadGraph(graph.getId());
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(persisted.getData());
        assertEquals("v", traces.getFirst().get("orientation"));
        assertEquals(List.of("Jan", "Feb"), traces.getFirst().get("x"));
        assertEquals(List.of(6L, 8L), traces.getFirst().get("y"));
    }

    @Test
    void updateLocationEventReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-event-update-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Monterey Park");
        ServiceEvent serviceEvent = createServiceEvent(
            location,
            "Original event",
            ServiceEventResponsibility.PARTNER,
            LocalDate.parse("2026-04-10"),
            LocalTime.parse("08:00:00"),
            LocalDate.parse("2026-04-10"),
            LocalTime.parse("09:00:00"),
            "Original description",
            ServiceEventStatus.UPCOMING
        );

        AuthCookies authCookies = loginAndCaptureCookies("partner-event-update-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}/events/{eventId}", location.getId(), serviceEvent.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Updated event",
                          "responsibility": "partner",
                          "date": "2026-04-11",
                          "time": "10:30:00",
                          "endDate": "2026-04-11",
                          "endTime": "11:45:00",
                          "description": "Updated description",
                          "status": "current"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/events", location.getId())
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
                    .param("month", "2026-04")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(updatedAt));
    }

    @Test
    void createGanttTaskReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-gantt-create-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Beverly Hills");
        AuthCookies authCookies = loginAndCaptureCookies("partner-gantt-create-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Task Alpha",
                          "startDate": "2026-05-01",
                          "endDate": "2026-05-03",
                          "description": "Task description",
                          "dependencyTaskIds": []
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(updatedAt));
    }

    @Test
    void createGanttTaskRejectsDescriptionLongerThanLimit() throws Exception {
        createUser("partner-gantt-description-limit@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Costa Mesa");
        AuthCookies authCookies = loginAndCaptureCookies("partner-gantt-description-limit@example.com", PASSWORD);
        String longDescription = "x".repeat(1025);

        mockMvc.perform(
                post("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Task Alpha",
                          "startDate": "2026-05-01",
                          "endDate": "2026-05-03",
                          "description": "%s",
                          "dependencyTaskIds": []
                        }
                        """.formatted(longDescription))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.fieldErrors.description").value("invalid_length"));
    }

    @Test
    void createGanttTasksBulkReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-gantt-bulk-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Glendale");
        AuthCookies authCookies = loginAndCaptureCookies("partner-gantt-bulk-timestamp@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/api/core/locations/{locationId}/gantt-tasks/bulk", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        [
                          {
                            "title": "Bulk task",
                            "startDate": "2026-05-04",
                            "endDate": "2026-05-06",
                            "description": "Bulk task description",
                            "dependencyTaskIds": []
                          }
                        ]
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(result.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(updatedAt));
    }

    @Test
    void updateGanttTaskReturnsPersistedUpdatedAt() throws Exception {
        createUser("partner-gantt-update-timestamp@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Pasadena");
        AuthCookies authCookies = loginAndCaptureCookies("partner-gantt-update-timestamp@example.com", PASSWORD);

        MvcResult createResult = mockMvc.perform(
                post("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Task Beta",
                          "startDate": "2026-05-02",
                          "endDate": "2026-05-04",
                          "description": "Before update",
                          "dependencyTaskIds": []
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        String taskId = extractJsonNumberField(createResult.getResponse().getContentAsString(), "id");
        MvcResult updateResult = mockMvc.perform(
                put("/api/core/locations/{locationId}/gantt-tasks/{taskId}", location.getId(), Long.parseLong(taskId))
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Task Beta Updated",
                          "startDate": "2026-05-03",
                          "endDate": "2026-05-06",
                          "description": "After update",
                          "dependencyTaskIds": []
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        String updatedAt = extractJsonStringField(updateResult.getResponse().getContentAsString(), "updatedAt");
        mockMvc.perform(
                get("/api/core/locations/{locationId}/gantt-tasks", location.getId())
                    .cookie(authCookies(authCookies))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].updatedAt").value(updatedAt));
    }

    @Test
    void updateLocationGraphsCanUpdateOneGraphWithoutTouchingOtherGraphs() throws Exception {
        AppUser partner = createUser("partner-graphs-partial@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Dana Point");
        Graph firstGraph = createGraph("First graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        Graph secondGraph = createGraph("Second graph", List.of(Map.of("type", "bar", "y", List.of(4, 5, 6))));
        addLocationGraph(location, firstGraph);
        addLocationGraph(location, secondGraph);
        Graph persistedFirst = reloadGraph(firstGraph.getId());

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-partial@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "expectedUpdatedAt": "%s",
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(firstGraph.getId(), persistedFirst.getUpdatedAt().toString()))
            )
            .andExpect(status().isNoContent());

        Graph updatedFirst = reloadGraph(firstGraph.getId());
        Graph unchangedSecond = reloadGraph(secondGraph.getId());

        List<Map<String, Object>> firstTraces = GraphPayloadMapper.toTraceList(updatedFirst.getData());
        List<Map<String, Object>> secondTraces = GraphPayloadMapper.toTraceList(unchangedSecond.getData());

        assertEquals("h", firstTraces.getFirst().get("orientation"));
        assertEquals(List.of(9L, 8L, 7L), firstTraces.getFirst().get("x"));
        assertEquals(List.of(4L, 5L, 6L), secondTraces.getFirst().get("x"));
    }

    @Test
    void updateLocationGraphsRejectsCookieAuthenticatedRequestWithoutExplicitCsrfHeader() throws Exception {
        createUser("partner-graphs-missing-csrf@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Seal Beach");
        Graph graph = createGraph("CSRF graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-missing-csrf@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void updateLocationGraphsRejectsMismatchedCsrfToken() throws Exception {
        createUser("partner-graphs-stale-csrf@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Brea");
        Graph graph = createGraph("Stale CSRF graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-stale-csrf@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    void updateLocationGraphsRefreshesExpiredAccessTokenBeforeCsrfRejection() throws Exception {
        AppUser partner = createUser("partner-graphs-expired-access-stale-csrf@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Placentia");
        Graph graph = createGraph("Expired access stale csrf graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies loginCookies = loginAndCaptureCookies("partner-graphs-expired-access-stale-csrf@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(partner, initialSession.getId());

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookiesWithCsrf(new AuthCookies(expiredAccessToken, loginCookies.refreshToken()), "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden())
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
    }

    @Test
    void updateLocationGraphsSucceedsAfterRefreshingCsrfTokenFromProfile() throws Exception {
        createUser("partner-graphs-refresh-csrf@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Lake Forest");
        Graph graph = createGraph("Refresh CSRF graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-graphs-refresh-csrf@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));

        String csrfToken = fetchCoreCsrfToken(authCookies);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookiesWithCsrf(authCookies, csrfToken))
                    .header("X-XSRF-TOKEN", csrfToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [5, 5, 5]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent());
    }

    @Test
    void updateLocationGraphsRejectsClientUsers() throws Exception {
        createUser("client-graphs@example.com", PASSWORD, true, "client");
        Location location = createLocation("Irvine");
        Graph graph = createGraph("Client graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("client-graphs@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void createLocationInviteRejectsMismatchedCsrfToken() throws Exception {
        createUser("partner-invite-stale-csrf@example.com", PASSWORD, true, "partner");
        AppUser invited = createUser("invite-target-stale-csrf@example.com", PASSWORD, true, "client");
        Location location = createLocation("San Clemente");
        AuthCookies authCookies = loginAndCaptureCookies("partner-invite-stale-csrf@example.com", PASSWORD);

        mockMvc.perform(
                post("/api/core/location-invites")
                    .cookie(authCookiesWithCsrf(authCookies, "cookie-token-stale"))
                    .header("X-XSRF-TOKEN", "header-token-stale")
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"locationId": %d, "invitedEmail": "%s"}
                        """.formatted(location.getId(), invited.getEmail()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("csrf_invalid"));
    }

    @Test
    void updateLocationGraphsRejectsGraphNotAssignedToLocation() throws Exception {
        createUser("partner-missing-graph@example.com", PASSWORD, true, "partner");
        Location targetLocation = createLocation("Anaheim");
        Location otherLocation = createLocation("Fullerton");
        Graph otherGraph = createGraph("Other location graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(otherLocation, otherGraph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-missing-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", targetLocation.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(otherGraph.getId()))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("location_graph_not_found"));
    }

    @Test
    void updateLocationGraphsRejectsUnknownLocation() throws Exception {
        createUser("partner-unknown-location-graph@example.com", PASSWORD, true, "partner");
        Graph graph = createGraph("Unknown location graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));

        AuthCookies authCookies = loginAndCaptureCookies("partner-unknown-location-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", 999999L)
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("location_not_found"));
    }

    @Test
    void updateLocationGraphsRejectsInvalidGraphDataPayload() throws Exception {
        createUser("partner-invalid-graph@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Tustin");
        Graph graph = createGraph("Invalid graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-invalid-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar"}, "bad-entry"]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_data_invalid"));
    }

    @Test
    void updateLocationGraphsRejectsDuplicateGraphIds() throws Exception {
        createUser("partner-duplicate-graph@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Garden Grove");
        Graph graph = createGraph("Duplicate graph test", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies authCookies = loginAndCaptureCookies("partner-duplicate-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            },
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [1, 1, 1]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId(), graph.getId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("graph_update_duplicates"));
    }

    @Test
    void updateLocationGraphsAllowsUnchangedSectionLayoutSnapshotEvenWhenLocationGraphSetHasExtraAssignments() throws Exception {
        createUser("partner-layout-snapshot@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Laguna Hills");
        Graph primaryGraph = createGraph("Primary graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        Graph extraGraph = createGraph("Extra graph", List.of(Map.of("type", "bar", "y", List.of(4, 5, 6))));
        addLocationGraph(location, primaryGraph);
        addLocationGraph(location, extraGraph);
        location.setSectionLayout(Map.of(
            "sections",
            List.of(Map.of(
                "section_id", 1,
                "graph_ids", List.of(primaryGraph.getId())
            ))
        ));
        locationRepository.saveAndFlush(location);

        AuthCookies authCookies = loginAndCaptureCookies("partner-layout-snapshot@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ],
                          "sectionLayout": {
                            "sections": [
                              {
                                "section_id": 1,
                                "graph_ids": [%d]
                              }
                            ]
                          }
                        }
                        """.formatted(primaryGraph.getId(), primaryGraph.getId()))
            )
            .andExpect(status().isNoContent());

        Graph persistedPrimary = reloadGraph(primaryGraph.getId());
        List<Map<String, Object>> persistedTraces = GraphPayloadMapper.toTraceList(persistedPrimary.getData());
        assertEquals("h", persistedTraces.getFirst().get("orientation"));
        assertEquals(List.of(9L, 8L, 7L), persistedTraces.getFirst().get("x"));
    }

    @Test
    void updateLocationGraphsStillRejectsChangedSectionLayoutWhenLocationGraphSetHasExtraAssignments() throws Exception {
        createUser("partner-layout-conflict@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Laguna Niguel");
        Graph primaryGraph = createGraph("Primary graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        Graph extraGraph = createGraph("Extra graph", List.of(Map.of("type", "bar", "y", List.of(4, 5, 6))));
        addLocationGraph(location, primaryGraph);
        addLocationGraph(location, extraGraph);
        location.setSectionLayout(Map.of(
            "sections",
            List.of(Map.of(
                "section_id", 1,
                "graph_ids", List.of(primaryGraph.getId())
            ))
        ));
        locationRepository.saveAndFlush(location);

        AuthCookies authCookies = loginAndCaptureCookies("partner-layout-conflict@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ],
                          "sectionLayout": {
                            "sections": [
                              {
                                "section_id": 1,
                                "graph_ids": [%d]
                              },
                              {
                                "section_id": 2,
                                "graph_ids": []
                              }
                            ]
                          }
                        }
                        """.formatted(primaryGraph.getId(), primaryGraph.getId()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("graph_update_conflict"));
    }

    @Test
    void updateLocationGraphsRejectsStaleExpectedUpdatedAtWithConflict() throws Exception {
        createUser("partner-graph-conflict@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Mission Viejo");
        Graph graph = createGraph("Conflict graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);
        Graph persisted = graphRepository.findById(graph.getId()).orElseThrow();
        String staleTimestamp = persisted.getUpdatedAt().minusSeconds(60).toString();

        AuthCookies authCookies = loginAndCaptureCookies("partner-graph-conflict@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "expectedUpdatedAt": "%s",
                              "data": [{"type": "bar", "y": [9, 8, 7]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId(), staleTimestamp))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("graph_update_conflict"))
            .andExpect(jsonPath("$.message").value("Graph update conflict"));
    }

    @Test
    void updateLocationGraphsIsTransactionalWhenBatchContainsInvalidGraph() throws Exception {
        createUser("partner-transaction-graph@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Westminster");
        Graph firstGraph = createGraph("First graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        Graph secondGraph = createGraph("Second graph", List.of(Map.of("type", "bar", "y", List.of(4, 5, 6))));
        addLocationGraph(location, firstGraph);
        // secondGraph intentionally left unassigned to fail the batch

        AuthCookies authCookies = loginAndCaptureCookies("partner-transaction-graph@example.com", PASSWORD);

        mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(authCookies))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [9, 9, 9]}]
                            },
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [8, 8, 8]}]
                            }
                          ]
                        }
                        """.formatted(firstGraph.getId(), secondGraph.getId()))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("location_graph_not_found"));

        Graph persistedFirst = reloadGraph(firstGraph.getId());
        List<Map<String, Object>> persistedTraces = GraphPayloadMapper.toTraceList(persistedFirst.getData());
        assertEquals("h", persistedTraces.getFirst().get("orientation"));
        assertEquals(List.of(1L, 2L, 3L), persistedTraces.getFirst().get("x"));
    }

    @Test
    void updateLocationGraphsAutoRefreshesExpiredAccessTokenWhenRefreshTokenIsValid() throws Exception {
        AppUser partner = createUser("partner-refresh-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Costa Mesa");
        Graph graph = createGraph("Refresh graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);

        AuthCookies loginCookies = loginAndCaptureCookies("partner-refresh-graphs@example.com", PASSWORD);
        AuthSession initialSession = authSessionRepository.findAll().getFirst();
        String expiredAccessToken = createExpiredAccessToken(partner, initialSession.getId());

        MvcResult result = mockMvc.perform(
                put("/api/core/locations/{locationId}/graphs", location.getId())
                    .cookie(authCookies(expiredAccessToken, loginCookies.refreshToken()))
                    .with(csrfDoubleSubmit())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {
                          "graphs": [
                            {
                              "graphId": %d,
                              "data": [{"type": "bar", "y": [5, 5, 5]}]
                            }
                          ]
                        }
                        """.formatted(graph.getId()))
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> rotatedCookies = readSetCookies(result);
        assertNotNull(rotatedCookies.get(AuthCookieNames.ACCESS_COOKIE_NAME));
        assertNotNull(rotatedCookies.get(AuthCookieNames.REFRESH_COOKIE_NAME));
    }

    @Test
    void updateLocationGraphsSupportsConcurrentWritesWithoutServerErrors() throws Exception {
        int requestCount = 4;
        AppUser partner = createUser("partner-concurrent-graphs@example.com", PASSWORD, true, "partner");
        Location location = createLocation("Laguna Beach");
        Graph graph = createGraph("Concurrent graph", List.of(Map.of("type", "bar", "y", List.of(1, 2, 3))));
        addLocationGraph(location, graph);
        AuthCookies authCookies = loginAndCaptureCookies("partner-concurrent-graphs@example.com", PASSWORD);

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                int value = i + 10;
                futures.add(executor.submit(concurrentGraphUpdateTask(
                    ready,
                    start,
                    authCookies,
                    location.getId(),
                    graph.getId(),
                    value
                )));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<MvcResult> future : futures) {
                MvcResult response = future.get(10, TimeUnit.SECONDS);
                assertEquals(HttpStatus.NO_CONTENT.value(), response.getResponse().getStatus());
            }
        } finally {
            executor.shutdownNow();
        }

        Graph persisted = reloadGraph(graph.getId());
        List<Map<String, Object>> traces = GraphPayloadMapper.toTraceList(persisted.getData());
        assertEquals(1, traces.size());
        assertThat(traces.getFirst()).containsEntry("type", "bar");
        assertThat(traces.getFirst()).containsEntry("orientation", "h");
    }

    private Callable<MvcResult> concurrentLocationRequestTask(
        CountDownLatch ready,
        CountDownLatch start,
        String expiredAccessToken,
        String refreshToken
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(
                    get("/api/core/locations")
                        .cookie(
                            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, expiredAccessToken),
                            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, refreshToken)
                        )
                )
                .andReturn();
        };
    }

    private Callable<MvcResult> concurrentGraphUpdateTask(
        CountDownLatch ready,
        CountDownLatch start,
        AuthCookies authCookies,
        Long locationId,
        Long graphId,
        int nextValue
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(
                    put("/api/core/locations/{locationId}/graphs", locationId)
                        .cookie(authCookies(authCookies))
                        .with(csrfDoubleSubmit())
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "graphs": [
                                {
                                  "graphId": %d,
                                  "data": [{"type":"bar","y":[%d,%d,%d]}]
                                }
                              ]
                            }
                            """.formatted(graphId, nextValue, nextValue, nextValue))
                )
                .andReturn();
        };
    }

    private Cookie[] authCookiesWithCsrf(AuthCookies authCookies, String csrfToken) {
        Cookie[] authPair = authCookies(authCookies);
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
        csrfCookie.setPath("/");
        return new Cookie[]{
            authPair[0],
            authPair[1],
            csrfCookie
        };
    }

    private String fetchCoreCsrfToken(AuthCookies authCookies) throws Exception {
        MvcResult profileResult = mockMvc.perform(
                get("/api/core/profile")
                    .cookie(authCookies(authCookies))
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andReturn();
        String csrfToken = readSetCookies(profileResult).get("XSRF-TOKEN");
        if (csrfToken != null && !csrfToken.isBlank()) {
            return csrfToken;
        }
        // CookieCsrfTokenRepository validates by matching header and cookie values.
        return UUID.randomUUID().toString();
    }

    private String extractJsonStringField(String json, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(json);
        assertTrue(matcher.find(), () -> "Expected JSON string field \"" + fieldName + "\" in: " + json);
        return matcher.group(1);
    }

    private String extractJsonNumberField(String json, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)")
            .matcher(json);
        assertTrue(matcher.find(), () -> "Expected JSON numeric field \"" + fieldName + "\" in: " + json);
        return matcher.group(1);
    }

    private byte[] createPngThumbnail() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createServiceCalendarSpreadsheet(List<String> headerRow, List<String> dataRow) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Service Calendar");
            writeSpreadsheetRow(sheet.createRow(0), headerRow);
            writeSpreadsheetRow(sheet.createRow(1), dataRow);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeSpreadsheetRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index += 1) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}
