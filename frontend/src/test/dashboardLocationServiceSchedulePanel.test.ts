import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("@solidjs/router", () => ({
  useParams: () => ({locationId: "42"})
}));

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../context/ProfileContext", () => ({
  useProfile: () => ({
    profile: () => ({role: "partner"})
  })
}));

vi.mock("../context/LocationDetailContext", () => ({
  useLocationDetail: () => ({
    serviceCalendarStaging: {
      stagedImportedEvents: () => [],
      stagedDeletedEvents: () => [],
      stagedCalendarUndoStack: () => [],
      isImportingSpreadsheet: () => false,
      setIsImportingSpreadsheet: vi.fn(),
      isApplyingImportedEvents: () => false,
      setIsApplyingImportedEvents: vi.fn(),
      isImportedEventMutationBusy: () => false,
      hasStagedImportedEvents: () => false,
      hasPendingImportedEventChanges: () => false,
      stageImportedRequests: vi.fn(() => false),
      editStagedEvent: vi.fn(() => false),
      completeStagedEvent: vi.fn(() => false),
      deleteStagedEvent: vi.fn(() => false),
      queuePersistedEventDelete: vi.fn(() => false),
      undoLastCalendarMutation: vi.fn(),
      reset: vi.fn()
    }
  })
}));

vi.mock("../util/location/locationEventApi", () => ({
  createLocationEventById: vi.fn(),
  createLocationCorrectiveActionById: vi.fn(),
  fetchLocationEventsById: vi.fn(async () => []),
  getLocationEventTemplateDownloadUrl: vi.fn((host: string, locationId: string) =>
    host + "/api/core/locations/" + locationId + "/events/template"
  ),
  uploadLocationEventCalendarById: vi.fn(),
  updateLocationEventById: vi.fn()
}));

import {LocationServiceCalendarPanel} from "../pages/authenticated/panels/location/LocationServiceCalendarPanel";

describe("DashboardLocationServiceSchedulePanel", () => {
  it("renders a get started trigger while keeping the calendar interactive", () => {
    const html = renderToString(LocationServiceCalendarPanel);

    expect(html).toContain("Service Calendar");
    expect(html).toContain("Get started");
    expect(html).toContain("data-service-calendar-intro-trigger");
    expect(html).toContain("data-service-calendar-upload-input");
    expect(html).toContain("data-service-calendar-apply");
    expect(html).toContain("data-service-calendar-undo");
    expect(html).toContain("aria-label=\"Service schedule calendar\"");
    expect(html).toContain("data-service-calendar-day-trigger");
    expect(html).not.toContain("View previous, current, and upcoming service events.");
    expect(html).not.toContain("Click an empty day cell to create a service event.");
    expect(html).not.toContain("New Service Event");
  });
});
