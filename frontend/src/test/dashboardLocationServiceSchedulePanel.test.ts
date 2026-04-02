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

vi.mock("../util/location/locationEventApi", () => ({
  createLocationEventById: vi.fn(),
  fetchLocationEventsById: vi.fn(async () => []),
  updateLocationEventById: vi.fn()
}));

import {DashboardLocationServiceCalendarPanel} from "../pages/authenticated/panels/location/DashboardLocationServiceCalendarPanel";

describe("DashboardLocationServiceSchedulePanel", () => {
  it("explains day-cell creation and renders interactive calendar cells", () => {
    const html = renderToString(DashboardLocationServiceCalendarPanel);

    expect(html).toContain("Service Calendar");
    expect(html).toContain("Click an empty day cell to create a service event.");
    expect(html).toContain("aria-label=\"Service schedule calendar\"");
    expect(html).toContain("data-service-calendar-day-trigger");
    expect(html).not.toContain("New Service Event");
  });
});
