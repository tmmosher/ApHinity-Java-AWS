import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("corvu/popover", async () => {
  const actual = await vi.importActual<typeof import("corvu/popover")>("corvu/popover");
  const Popover = actual.default;
  Popover.Portal = (props: {children: unknown}) => props.children;
  return {default: Popover};
});

vi.mock("../util/location/serviceCalendarFilters", async () => {
  const actual = await vi.importActual<typeof import("../util/location/serviceCalendarFilters")>(
    "../util/location/serviceCalendarFilters"
  );

  return {
    ...actual,
    createDefaultServiceCalendarFilters: () => ({
      responsibility: {
        enabled: false,
        value: ""
      },
      date: {
        enabled: true,
        value: "2026-05-01"
      },
      status: {
        enabled: false,
        value: ""
      }
    })
  };
});

import ServiceScheduleCalendar from "../pages/authenticated/panels/location/ServiceScheduleCalendar";

describe("ServiceScheduleCalendar no-results state", () => {
  it("keeps the no-match message without showing the event count chip", () => {
    const html = renderToString(() => ServiceScheduleCalendar({
      month: new Date("2026-04-01T00:00:00"),
      events: [
        {
          id: 8,
          title: "Client kickoff",
          responsibility: "client",
          date: "2026-04-07",
          time: "09:00:00",
          endDate: "2026-04-07",
          endTime: "11:30:00",
          description: "Initial kickoff meeting",
          status: "upcoming",
          createdAt: "2026-03-25T00:00:00Z",
          updatedAt: "2026-03-25T00:00:00Z"
        }
      ]
    }));

    expect(html).toContain("data-service-calendar-toolbar");
    expect(html).toContain("data-service-calendar-filter-trigger");
    expect(html).toContain("No events match the selected filters.");
    expect(html).not.toContain("Showing ");
    expect((html.match(/data-service-event-bar/g) ?? [])).toHaveLength(0);
  });
});
