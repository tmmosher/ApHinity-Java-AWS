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

vi.mock("../pages/authenticated/panels/location/ServiceEventCreateModal", () => ({
  default: () => null
}));

vi.mock("../pages/authenticated/panels/location/ServiceEventEditModal", () => ({
  default: () => null
}));

import {DashboardLocationServiceCalendarPanel} from "../pages/authenticated/panels/location/DashboardLocationServiceCalendarPanel";

describe("DashboardLocationServiceSchedulePanel", () => {
  it("renders the event button above the service calendar", () => {
    const html = renderToString(DashboardLocationServiceCalendarPanel);

    expect(html).toContain("Service Calendar");
    expect(html).toContain("New Service Event");
    expect(html).toContain("data-corvu-calendar-table");
    expect(html.indexOf("New Service Event")).toBeLessThan(html.indexOf("data-corvu-calendar-table"));
  });
});
