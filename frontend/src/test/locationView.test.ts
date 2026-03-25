import {describe, expect, it} from "vitest";
import type {DashboardLocationView} from "../pages/authenticated/panels/location/locationView";
import {
  createLocationViewActive,
  dashboardLocationViews,
  getFreshLocationScopedValue,
  getNextLocationGraphRequestId,
  getLocationViewFromPathname,
  getLocationViewHref,
  isFreshLocationScopedResource,
  normalizeLocationPathname
} from "../pages/authenticated/panels/location/locationView";

describe("locationView helpers", () => {
  it("keeps the selector views ordered left-to-right", () => {
    expect(
      dashboardLocationViews.map(({view, name}) => ({view, name}))
    ).toEqual([
      {view: "service-schedule", name: "Service Schedule"},
      {view: "gantt-chart", name: "Gantt Chart"},
      {view: "dashboard", name: "Dashboard"}
    ]);
  });

  it("recomputes the active selector state when the route view changes", () => {
    let currentView: DashboardLocationView = "service-schedule";
    const getCurrentView = () => currentView;
    const serviceScheduleActive = createLocationViewActive(getCurrentView, "service-schedule");
    const dashboardActive = createLocationViewActive(getCurrentView, "dashboard");

    expect(serviceScheduleActive()).toBe(true);
    expect(dashboardActive()).toBe(false);

    currentView = "dashboard";

    expect(serviceScheduleActive()).toBe(false);
    expect(dashboardActive()).toBe(true);
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

  it("retains the graph request key across tab switches until a different location dashboard is opened", () => {
    expect(getNextLocationGraphRequestId(undefined, "42", "service-schedule")).toBeUndefined();
    expect(getNextLocationGraphRequestId(undefined, "42", "dashboard")).toBe("42");
    expect(getNextLocationGraphRequestId("42", "42", "service-schedule")).toBe("42");
    expect(getNextLocationGraphRequestId("42", "42", "gantt-chart")).toBe("42");
    expect(getNextLocationGraphRequestId("42", "42", "dashboard")).toBe("42");
    expect(getNextLocationGraphRequestId("42", "7", "service-schedule")).toBe("42");
    expect(getNextLocationGraphRequestId("42", "7", "dashboard")).toBe("7");
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
