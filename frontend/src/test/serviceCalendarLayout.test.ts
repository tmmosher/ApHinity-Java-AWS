import {describe, expect, it} from "vitest";
import type {LocationServiceEvent} from "../types/Types";
import {listDateRangeInclusive, parseDateInputValue} from "../util/location/dateUtility";
import {buildServiceCalendarWeekLayouts} from "../util/location/serviceCalendarLayout";

const createWeek = (startDate: string): Date[] => {
  const start = parseDateInputValue(startDate);
  const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6);
  return listDateRangeInclusive(start, end);
};

const createServiceEvent = (overrides: Partial<LocationServiceEvent> & Pick<LocationServiceEvent, "id">): LocationServiceEvent => ({
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
  it("builds connected visible pieces across multiple days within a week", () => {
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

    expect(layout.visiblePiecesByDay[2][0]).toMatchObject({
      event: expect.objectContaining({id: 9}),
      isStart: true,
      isEnd: false
    });
    expect(layout.visiblePiecesByDay[3][0]).toMatchObject({
      event: expect.objectContaining({id: 9}),
      isStart: false,
      isEnd: false
    });
    expect(layout.visiblePiecesByDay[4][0]).toMatchObject({
      event: expect.objectContaining({id: 9}),
      isStart: false,
      isEnd: true
    });
  });

  it("keeps continuation pieces open at the week boundary", () => {
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

    expect(layout.visiblePiecesByDay[0][0]).toMatchObject({
      event: expect.objectContaining({id: 11}),
      isStart: false,
      isEnd: false
    });
    expect(layout.visiblePiecesByDay[2][0]).toMatchObject({
      event: expect.objectContaining({id: 11}),
      isStart: false,
      isEnd: true
    });
  });

  it("moves overflowed events into per-day hidden lists", () => {
    const [layout] = buildServiceCalendarWeekLayouts(
      [createWeek("2026-04-05")],
      [
        createServiceEvent({id: 1, title: "Client kickoff", date: "2026-04-07"}),
        createServiceEvent({id: 2, title: "Partner inspection", responsibility: "partner", date: "2026-04-07", endDate: "2026-04-08"}),
        createServiceEvent({id: 3, title: "Client review", date: "2026-04-07"})
      ],
      2
    );

    expect(layout.visiblePiecesByDay[2][0]?.event.id).toBe(2);
    expect(layout.visiblePiecesByDay[2][1]?.event.id).toBe(1);
    expect(layout.hiddenEventsByDay[2]).toEqual([
      expect.objectContaining({id: 3})
    ]);
    expect(layout.hiddenEventsByDay[3]).toEqual([]);
  });
});
