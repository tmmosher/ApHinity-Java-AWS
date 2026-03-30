import {describe, expect, it} from "vitest";
import {
  compareDates,
  formatDateInputValue,
  formatLocationEventMonth,
  isSameCalendarMonth,
  listDateRangeInclusive,
  normalizeMonthStart,
  normalizeYearMonth,
  parseDateInputValue
} from "../util/location/dateUtility";

describe("dateUtility", () => {
  it("formats dates for date inputs and month queries", () => {
    const date = new Date(2026, 2, 25, 9, 30);

    expect(formatDateInputValue(date)).toBe("2026-03-25");
    expect(formatLocationEventMonth(date)).toBe("2026-03");
  });

  it("normalizes viewed months to the first day of the month", () => {
    const normalized = normalizeMonthStart(new Date(2026, 3, 18, 14, 45));

    expect(normalized.getFullYear()).toBe(2026);
    expect(normalized.getMonth()).toBe(3);
    expect(normalized.getDate()).toBe(1);
  });

  it("lists inclusive date ranges for multi-day calendar events", () => {
    const dates = listDateRangeInclusive(
      parseDateInputValue("2026-04-07"),
      parseDateInputValue("2026-04-09")
    );

    expect(dates.map(formatDateInputValue)).toEqual([
      "2026-04-07",
      "2026-04-08",
      "2026-04-09"
    ]);
  });

  it("compares dates and checks calendar-month equality", () => {
    const aprilStart = new Date("2026-04-01T00:00:00");
    const aprilEnd = new Date("2026-04-30T12:00:00");
    const mayStart = new Date("2026-05-01T00:00:00");

    expect(compareDates(aprilStart, aprilEnd)).toBe(-1);
    expect(compareDates(aprilEnd, aprilStart)).toBe(1);
    expect(compareDates(aprilStart, new Date("2026-04-01T00:00:00"))).toBe(0);
    expect(isSameCalendarMonth(aprilStart, aprilEnd)).toBe(true);
    expect(isSameCalendarMonth(aprilStart, mayStart)).toBe(false);
  });

  it("normalizes valid year-month values and rejects invalid ones", () => {
    expect(normalizeYearMonth(" 2026-04 ")).toBe("2026-04");
    expect(() => normalizeYearMonth("2026/04")).toThrowError("Invalid service event month");
  });
});
