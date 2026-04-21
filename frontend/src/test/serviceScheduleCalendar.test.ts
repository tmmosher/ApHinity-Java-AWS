import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("corvu/popover", async () => {
  const actual = await vi.importActual<typeof import("corvu/popover")>("corvu/popover");
  const Popover = actual.default;
  Popover.Portal = (props: { children: unknown }) => props.children;
  return { default: Popover };
});

import ServiceScheduleCalendar, {
  resolveServiceCalendarTransitionDirection
} from "../pages/authenticated/panels/location/ServiceScheduleCalendar";
import {requestServiceEventEdit} from "../components/service-editor/ServiceEventEditPopover";

describe("ServiceScheduleCalendar", () => {
  it("switches the event popover into edit mode", () => {
    const setIsEditing = vi.fn();

    requestServiceEventEdit(setIsEditing);

    expect(setIsEditing).toHaveBeenCalledWith(true);
  });

  it("renders week-row spanning bars and an overflow trigger for crowded days", () => {
    const html = renderToString(() => ServiceScheduleCalendar({
      month: new Date("2026-04-01T00:00:00"),
      eventEditorRole: "partner",
      onCreateEventSave: async () => undefined,
      onEditEventSave: async () => undefined,
      canEditEvent: () => true,
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
          isCorrectiveAction: true,
          correctiveActionSourceEventId: 3,
          correctiveActionSourceEventTitle: "Monthly maintenance",
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
    expect(html).toContain("data-service-calendar-toolbar");
    expect(html).toContain("data-service-calendar-filter-trigger");
    expect(html).toContain("data-service-calendar-transition-surface");
    expect(html).toContain("service-calendar-transition-surface h-full min-h-0");
    expect(html.indexOf("Viewing Month")).toBeLessThan(html.indexOf("data-service-calendar-toolbar"));
    expect(html.indexOf("data-service-calendar-toolbar")).toBeLessThan(html.indexOf("data-service-calendar-transition-surface"));
    expect((html.match(/data-corvu-calendar-celltrigger/g) ?? [])).toHaveLength(42);
    expect((html.match(/data-service-calendar-day-trigger/g) ?? [])).toHaveLength(42);
    expect((html.match(/data-service-event-create-popover/g) ?? [])).toHaveLength(0);
    expect((html.match(/data-service-calendar-week-row/g) ?? [])).toHaveLength(6);
    expect((html.match(/data-service-event-bar/g) ?? [])).toHaveLength(2);
    expect((html.match(/data-service-event-overflow-trigger/g) ?? [])).toHaveLength(1);
    expect((html.match(/data-service-event-corrective-action-icon/g) ?? []).length).toBeGreaterThan(0);
    expect(html).toContain("Client kickoff");
    expect(html).toContain("Partner inspection");
    expect(html).toContain("grid-column:3 / 5");
    expect(html).toContain("grid-rows-[1.35rem_1.05rem_1.05rem_1.05rem]");
    expect(html).toContain("...");
    expect(html).toContain("bg-[#f59e0b]/18");
    expect(html).toContain("bg-[#dcfce7]");
    expect(html).toContain("cursor-pointer");
    expect(html).toContain("hover:-translate-y-px");
    expect(html).toContain("active:translate-y-px");
  });

  it("resolves swipe directions from the current month to the next month", () => {
    expect(resolveServiceCalendarTransitionDirection(
      new Date("2026-04-01T00:00:00"),
      new Date("2026-03-01T00:00:00")
    )).toBe("previous");
    expect(resolveServiceCalendarTransitionDirection(
      new Date("2026-04-01T00:00:00"),
      new Date("2026-05-01T00:00:00")
    )).toBe("next");
    expect(resolveServiceCalendarTransitionDirection(
      new Date("2026-04-01T00:00:00"),
      new Date("2026-04-15T00:00:00")
    )).toBeUndefined();
    expect(resolveServiceCalendarTransitionDirection(
      new Date("2026-12-01T00:00:00"),
      new Date("2027-01-01T00:00:00")
    )).toBe("next");
    expect(resolveServiceCalendarTransitionDirection(
      new Date("2026-01-01T00:00:00"),
      new Date("2025-12-01T00:00:00")
    )).toBe("previous");
  });
});
