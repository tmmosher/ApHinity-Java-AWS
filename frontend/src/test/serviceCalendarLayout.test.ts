import {describe, expect, it} from "vitest";
import type {LocationServiceEvent} from "../types/Types";
import {listDateRangeInclusive, parseDateInputValue} from "../util/location/dateUtility";
import {buildServiceCalendarWeekLayouts} from "../util/location/serviceCalendarLayout";

const createWeek = (startDate: string): Date[] => {
  const start = parseDateInputValue(startDate);
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6);
  return listDateRangeInclusive(start, end);
};

const createServiceEvent = (
  overrides: Partial<LocationServiceEvent> & Pick<LocationServiceEvent, "id">
): LocationServiceEvent => ({
  id: overrides.id,
  title: overrides.title ?? `Event ${overrides.id}`,
  description: overrides.description ?? null,
  responsibility: overrides.responsibility ?? "client",
  status: overrides.status ?? "upcoming",
  date: overrides.date ?? "2026-04-07",
  time: overrides.time ?? "09:00:00",
  endDate: overrides.endDate ?? overrides.date ?? "2026-04-07",
  endTime: overrides.endTime ?? "10:00:00",
  createdAt: overrides.createdAt ?? "2026-03-25T00:00:00Z",
  updatedAt: overrides.updatedAt ?? "2026-03-25T00:00:00Z"
});

describe("serviceCalendarLayout", () => {
  it("builds visible week segments for multi-day events", () => {
    const [layout] = buildServiceCalendarWeekLayouts(
      [createWeek("2026-04-05")],
      [
        createServiceEvent({
          id: 9,
          title: "Partner inspection",
          responsibility: "partner",
          date: "2026-04-07",
          endDate: "2026-04-09"
        })
      ],
      2
    );

    expect(layout.visibleSegments).toEqual([
      expect.objectContaining({
        event: expect.objectContaining({id: 9}),
        lane: 0,
        startDayIndex: 2,
        endDayIndex: 4,
        startsWithinWeek: true,
        endsWithinWeek: true
      })
    ]);
  });

  it("keeps week-boundary continuation metadata on visible segments", () => {
    const [layout] = buildServiceCalendarWeekLayouts(
      [createWeek("2026-04-05")],
      [
        createServiceEvent({
          id: 11,
          title: "Boundary event",
          date: "2026-04-03",
          endDate: "2026-04-07"
        })
      ],
      2
    );

    expect(layout.visibleSegments).toEqual([
      expect.objectContaining({
        event: expect.objectContaining({id: 11}),
        lane: 0,
        startDayIndex: 0,
        endDayIndex: 2,
        startsWithinWeek: false,
        endsWithinWeek: true
      })
    ]);
  });

  it("moves overflowed events into per-day hidden lists", () => {
    const [layout] = buildServiceCalendarWeekLayouts(
      [createWeek("2026-04-05")],
      [
        createServiceEvent({id: 1, title: "Client kickoff", date: "2026-04-07"}),
        createServiceEvent({
          id: 2,
          title: "Partner inspection",
          responsibility: "partner",
          date: "2026-04-07",
          endDate: "2026-04-08"
        }),
        createServiceEvent({id: 3, title: "Client review", date: "2026-04-07"})
      ],
      2
    );

    expect(layout.visibleSegments.map((segment) => segment.event.id)).toEqual([2, 1]);
    expect(layout.hiddenEventsByDay[2]).toEqual([
      expect.objectContaining({id: 3})
    ]);
    expect(layout.hiddenEventsByDay[3]).toEqual([]);
  });

  it("splits an event across adjacent calendar weeks with correct boundary metadata", () => {
    const layouts = buildServiceCalendarWeekLayouts(
      [
        createWeek("2026-04-05"),
        createWeek("2026-04-12")
      ],
      [
        createServiceEvent({
          id: 21,
          title: "Cross-week event",
          date: "2026-04-10",
          endDate: "2026-04-13"
        })
      ],
      2
    );

    expect(layouts[0].visibleSegments).toEqual([
      expect.objectContaining({
        event: expect.objectContaining({id: 21}),
        startDayIndex: 5,
        endDayIndex: 6,
        startsWithinWeek: true,
        endsWithinWeek: false
      })
    ]);
    expect(layouts[1].visibleSegments).toEqual([
      expect.objectContaining({
        event: expect.objectContaining({id: 21}),
        startDayIndex: 0,
        endDayIndex: 1,
        startsWithinWeek: false,
        endsWithinWeek: true
      })
    ]);
  });

  it("orders same-day events chronologically before using fallbacks", () => {
    const [layout] = buildServiceCalendarWeekLayouts(
      [createWeek("2026-04-05")],
      [
        createServiceEvent({
          id: 40,
          title: "Afternoon event",
          date: "2026-04-07",
          time: "15:00:00"
        }),
        createServiceEvent({
          id: 41,
          title: "Morning event",
          date: "2026-04-07",
          time: "08:30:00"
        })
      ],
      2
    );

    expect(layout.visibleSegments.map((segment) => segment.event.id)).toEqual([41, 40]);
  });
});
