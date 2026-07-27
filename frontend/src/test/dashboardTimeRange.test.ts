import {describe, expect, it} from "vitest";
import {
  monthRangeForDashboardTimeRange,
  selectCommonDashboardTimeRange
} from "../util/location/dashboardTimeRange";
import {vi} from "vitest";

describe("dashboardTimeRange helpers", () => {
  it("maps dashboard time ranges to graph request month ranges", () => {
    expect(monthRangeForDashboardTimeRange("threeMonths")).toBe(3);
    expect(monthRangeForDashboardTimeRange("twelveMonths")).toBe(12);
    expect(monthRangeForDashboardTimeRange("allTime")).toBe(-1);
  });

  it("invalidates the full graph cache before every common selection, including the active range", () => {
    const invalidateGraphCache = vi.fn();
    const setGraphTimeRange = vi.fn();

    selectCommonDashboardTimeRange("threeMonths", {invalidateGraphCache, setGraphTimeRange});

    expect(invalidateGraphCache).toHaveBeenCalledOnce();
    expect(setGraphTimeRange).toHaveBeenCalledWith("threeMonths");
    expect(invalidateGraphCache.mock.invocationCallOrder[0])
      .toBeLessThan(setGraphTimeRange.mock.invocationCallOrder[0]);
  });
});
