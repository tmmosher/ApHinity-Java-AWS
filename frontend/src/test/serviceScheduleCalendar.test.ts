import {renderToString} from "solid-js/web";
import {describe, expect, it} from "vitest";
import ServiceScheduleCalendar from "../pages/authenticated/panels/location/ServiceScheduleCalendar";

describe("ServiceScheduleCalendar", () => {
  it("renders a full six-week calendar shell with navigation and interactive cell styling", () => {
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
        },
        {
          id: 9,
          title: "Partner inspection",
          responsibility: "partner",
          date: "2026-04-07",
          time: "13:00:00",
          endDate: "2026-04-08",
          endTime: "15:00:00",
          description: null,
          status: "current",
          createdAt: "2026-03-25T00:00:00Z",
          updatedAt: "2026-03-25T00:00:00Z"
        }
      ]
    }));

    expect(html).toContain("Service schedule calendar");
    expect(html).toContain("Go to previous month");
    expect(html).toContain("Go to next month");
    expect((html.match(/data-corvu-calendar-headcell/g) ?? [])).toHaveLength(7);
    expect((html.match(/data-corvu-calendar-celltrigger/g) ?? [])).toHaveLength(42);
    expect((html.match(/data-service-event-bar/g) ?? [])).toHaveLength(3);
    expect(html).toContain("Client kickoff");
    expect(html).toContain("Partner inspection");
    expect(html).not.toContain("9:00");
    expect(html).not.toContain("11:30");
    expect(html).toContain("bg-[#f59e0b]/18");
    expect(html).toContain("bg-[#dcfce7]");
    expect(html).toContain("hover:-translate-y-px");
    expect(html).toContain("active:translate-y-px");
  });
});
