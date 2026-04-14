import {describe, expect, it} from "vitest";
import {
  countActiveServiceCalendarFilters,
  createDefaultServiceCalendarFilters,
  filterLocationServiceEvents
} from "../util/location/serviceCalendarFilters";
import type {LocationServiceEvent} from "../types/Types";

const createEvent = (overrides: Partial<LocationServiceEvent> & Pick<LocationServiceEvent, "id">): LocationServiceEvent => ({
  id: overrides.id,
  title: overrides.title ?? "Service event",
  responsibility: overrides.responsibility ?? "client",
  date: overrides.date ?? "2026-04-07",
  time: overrides.time ?? "09:00:00",
  endDate: overrides.endDate ?? overrides.date ?? "2026-04-07",
  endTime: overrides.endTime ?? "11:30:00",
  description: overrides.description ?? null,
  status: overrides.status ?? "upcoming",
  createdAt: overrides.createdAt ?? "2026-03-25T00:00:00Z",
  updatedAt: overrides.updatedAt ?? "2026-03-25T00:00:00Z"
});

describe("serviceCalendarFilters", () => {
  it("starts with every filter disabled", () => {
    expect(createDefaultServiceCalendarFilters()).toEqual({
      responsibility: {
        enabled: false,
        value: ""
      },
      date: {
        enabled: false,
        value: ""
      },
      status: {
        enabled: false,
        value: ""
      }
    });
  });

  it("counts only enabled filters with a selected value", () => {
    expect(countActiveServiceCalendarFilters({
      responsibility: {
        enabled: true,
        value: "client"
      },
      date: {
        enabled: true,
        value: ""
      },
      status: {
        enabled: true,
        value: "completed"
      }
    })).toBe(2);
  });

  it("filters by responsibility, status, and inclusive date range as an and-chain", () => {
    const events = [
      createEvent({
        id: 1,
        title: "Client review",
        responsibility: "client",
        date: "2026-04-07",
        endDate: "2026-04-08",
        status: "current"
      }),
      createEvent({
        id: 2,
        title: "Partner visit",
        responsibility: "partner",
        date: "2026-04-08",
        endDate: "2026-04-08",
        status: "current"
      }),
      createEvent({
        id: 3,
        title: "Client closeout",
        responsibility: "client",
        date: "2026-04-08",
        endDate: "2026-04-08",
        status: "completed"
      })
    ];

    const filtered = filterLocationServiceEvents(events, {
      responsibility: {
        enabled: true,
        value: "client"
      },
      date: {
        enabled: true,
        value: "2026-04-08"
      },
      status: {
        enabled: true,
        value: "current"
      }
    });

    expect(filtered.map((event) => event.id)).toEqual([1]);
  });

  it("ignores incomplete filter controls until a value is chosen", () => {
    const events = [
      createEvent({id: 1, responsibility: "client"}),
      createEvent({id: 2, responsibility: "partner"})
    ];

    const filtered = filterLocationServiceEvents(events, {
      responsibility: {
        enabled: true,
        value: ""
      },
      date: {
        enabled: false,
        value: ""
      },
      status: {
        enabled: false,
        value: ""
      }
    });

    expect(filtered.map((event) => event.id)).toEqual([1, 2]);
  });

  it("ignores invalid date values instead of reporting them as active filters", () => {
    const filters = {
      responsibility: {
        enabled: false,
        value: ""
      },
      date: {
        enabled: true,
        value: "not-a-date"
      },
      status: {
        enabled: true,
        value: "upcoming"
      }
    } as const;

    expect(countActiveServiceCalendarFilters(filters)).toBe(1);
    expect(filterLocationServiceEvents([
      createEvent({id: 1, status: "upcoming"}),
      createEvent({id: 2, status: "completed"})
    ], filters).map((event) => event.id)).toEqual([1]);
  });
});
