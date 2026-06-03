import {describe, expect, it} from "vitest";
import {monthRangeForDashboardTimeRange} from "../util/location/dashboardTimeRange";

describe("dashboardTimeRange helpers", () => {
  it("maps dashboard time ranges to graph request month ranges", () => {
    expect(monthRangeForDashboardTimeRange("threeMonths")).toBe(3);
    expect(monthRangeForDashboardTimeRange("twelveMonths")).toBe(12);
    expect(monthRangeForDashboardTimeRange("allTime")).toBe(-1);
  });
});
