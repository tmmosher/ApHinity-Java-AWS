import {describe, expect, it} from "vitest";
import {
  dashboardLocationViews,
  getFreshLocationScopedValue,
  getLocationViewFromPathname,
  getLocationViewHref,
  isFreshLocationScopedResource,
  normalizeLocationPathname
} from "../pages/authenticated/panels/location/locationView";

describe("locationView helpers", () => {
  it("keeps the selector views ordered left-to-right", () => {
    expect(dashboardLocationViews).toEqual([
      {view: "service-schedule", label: "Service Schedule"},
      {view: "gantt-chart", label: "Gantt Chart"},
      {view: "dashboard", label: "Dashboard"}
    ]);
  });

  it("normalizes trailing slashes while preserving the root path", () => {
    expect(normalizeLocationPathname("/dashboard/locations/42/")).toBe("/dashboard/locations/42");
    expect(normalizeLocationPathname("/dashboard/locations/42/dashboard///")).toBe("/dashboard/locations/42/dashboard");
    expect(normalizeLocationPathname("/")).toBe("/");
  });

  it("maps location routes to the expected selector view", () => {
    expect(getLocationViewFromPathname("/dashboard/locations/42", "42")).toBe("service-schedule");
    expect(getLocationViewFromPathname("/dashboard/locations/42/service-schedule/", "42")).toBe("service-schedule");
    expect(getLocationViewFromPathname("/dashboard/locations/42/gantt-chart", "42")).toBe("gantt-chart");
    expect(getLocationViewFromPathname("/dashboard/locations/42/dashboard", "42")).toBe("dashboard");
  });

  it("builds location view hrefs for each selector tab", () => {
    expect(getLocationViewHref("42", "service-schedule")).toBe("/dashboard/locations/42");
    expect(getLocationViewHref("42", "gantt-chart")).toBe("/dashboard/locations/42/gantt-chart");
    expect(getLocationViewHref("42", "dashboard")).toBe("/dashboard/locations/42/dashboard");
  });

  it("only treats location-scoped resources as fresh when the ids match", () => {
    const freshResource = {
      locationId: "42",
      value: {name: "Austin"}
    };
    const staleResource = {
      locationId: "7",
      value: {name: "Phoenix"}
    };

    expect(isFreshLocationScopedResource("42", freshResource)).toBe(true);
    expect(isFreshLocationScopedResource("42", staleResource)).toBe(false);
    expect(isFreshLocationScopedResource("42", undefined)).toBe(false);
    expect(getFreshLocationScopedValue("42", freshResource)).toEqual({name: "Austin"});
    expect(getFreshLocationScopedValue("42", staleResource)).toBeUndefined();
    expect(getFreshLocationScopedValue("42", undefined)).toBeUndefined();
  });
});
