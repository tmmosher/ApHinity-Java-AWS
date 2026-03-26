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
  createLocationEventById: vi.fn()
}));

vi.mock("../pages/authenticated/panels/location/ServiceEventCreateModal", () => ({
  default: () => null
}));

import {DashboardLocationServiceSchedulePanel} from "../pages/authenticated/panels/location/DashboardLocationServiceSchedulePanel";

describe("DashboardLocationServiceSchedulePanel", () => {
  it("renders the event button above the service calendar", () => {
    const html = renderToString(DashboardLocationServiceSchedulePanel);

    expect(html).toContain("Service Schedule");
    expect(html).toContain("New Service Event");
    expect(html).toContain("data-corvu-calendar-table");
    expect(html.indexOf("New Service Event")).toBeLessThan(html.indexOf("data-corvu-calendar-table"));
  });
});
