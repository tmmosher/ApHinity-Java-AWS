import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("corvu/popover", async () => {
  const actual = await vi.importActual<typeof import("corvu/popover")>("corvu/popover");
  const Popover = actual.default;
  Popover.Portal = (props: { children: unknown }) => props.children;
  return { default: Popover };
});

import ServiceScheduleCalendar, {
  requestServiceEventEdit
} from "../pages/authenticated/panels/location/ServiceScheduleCalendar";

describe("ServiceScheduleCalendar", () => {
  it("closes the popover before opening the edit modal", () => {
    const closePopover = vi.fn();
    const openEditor = vi.fn();
    const event = {
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
    } as const;

    requestServiceEventEdit(closePopover, event, openEditor);

    expect(closePopover).toHaveBeenCalledWith();
    expect(openEditor).toHaveBeenCalledWith(event);
  });

  it("renders connected multi-day event pieces and an overflow trigger for crowded days", () => {
    const html = renderToString(() => ServiceScheduleCalendar({
      month: new Date("2026-04-01T00:00:00"),
      canEditEvent: () => true,
      onEditEvent: () => undefined,
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
        },
        {
          id: 10,
          title: "Client review",
          responsibility: "client",
          date: "2026-04-07",
          time: "15:30:00",
          endDate: "2026-04-07",
          endTime: "16:00:00",
          description: "Quarterly review",
          status: "upcoming",
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
    expect((html.match(/data-service-event-overflow-trigger/g) ?? [])).toHaveLength(1);
    expect(html).toContain("Client kickoff");
    expect(html).toContain("Partner inspection");
    expect(html).toContain("h-[5.75rem]");
    expect(html).toContain("...");
    expect(html).toContain("rounded-r-none");
    expect(html).toContain("rounded-l-none");
    expect(html).toContain("bg-[#f59e0b]/18");
    expect(html).toContain("bg-[#dcfce7]");
    expect(html).toContain("hover:-translate-y-px");
    expect(html).toContain("active:translate-y-px");
  });
});
