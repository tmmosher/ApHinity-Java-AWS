import {Accessor, JSX} from "solid-js";
import {dashboard_icon, gantt_icon, service_icon} from "../../components/location/LocationIcons";

export type DashboardLocationView = "service-calendar" | "gantt-chart" | "dashboard";

export type LocationScopedResource<T> = {
  locationId: string;
  value: T;
};

export const dashboardLocationViews: Array<{
  view: DashboardLocationView;
  name: string;
  label: JSX.Element;
}> = [
  {view: "dashboard", name: "Dashboard", label: dashboard_icon},
  {view: "service-calendar", name: "Service Calendar", label: service_icon},
  {view: "gantt-chart", name: "Gantt Chart", label: gantt_icon},
];

export const createLocationViewActive = (
  currentView: Accessor<DashboardLocationView>,
  view: DashboardLocationView
): Accessor<boolean> => () => currentView() === view;

/**
 * Returns a guard that reports `true` only when the location id changes.
 *
 * This is used to reset local dashboard state after route transitions without
 * re-running the reset logic on every render.
 */
export const createDashboardLocationResetGuard = (initialLocationId: string) => {
  let previousLocationId = initialLocationId;

  return (nextLocationId: string): boolean => {
    if (nextLocationId === previousLocationId) {
      return false;
    }
    previousLocationId = nextLocationId;
    return true;
  };
};

/**
 * Keeps a nested location detail request aligned with the active view.
 *
 * Only the dashboard view swaps the location id directly; the calendar and
 * gantt views keep the previous request identity so async responses do not
 * overwrite the active subpanel.
 */
export const getNextLocationGraphRequestId = (
  currentRequestedLocationId: string | undefined,
  currentLocationId: string,
  currentView: DashboardLocationView
): string | undefined => (
  currentView === "dashboard"
    ? currentLocationId
    : currentRequestedLocationId
);

export const normalizeLocationPathname = (pathname: string): string => pathname.replace(/\/+$/, "") || "/";

export const getLocationViewFromPathname = (pathname: string, locationId: string): DashboardLocationView => {
  const normalizedPathname = normalizeLocationPathname(pathname);
  const basePath = `/dashboard/locations/${locationId}`;

  if (normalizedPathname === basePath || normalizedPathname === `${basePath}/dashboard`) {
    return "dashboard";
  }
  if (normalizedPathname === `${basePath}/service-calendar`) {
    return "service-calendar";
  }
  if (normalizedPathname === `${basePath}/gantt-chart`) {
    return "gantt-chart";
  }
  return "dashboard";
};

export const getLocationViewHref = (locationId: string, view: DashboardLocationView): string => {
  if (view === "dashboard") {
    return `/dashboard/locations/${locationId}`;
  }
  return `/dashboard/locations/${locationId}/${view}`;
};

/**
 * Checks whether a resource still belongs to the current location.
 *
 * The detail panels use this to discard stale async results when the user
 * navigates between locations while a request is still in flight.
 */
export const isFreshLocationScopedResource = <T>(
  currentLocationId: string,
  resource: LocationScopedResource<T> | undefined
): boolean => resource?.locationId === currentLocationId;

export const getFreshLocationScopedValue = <T>(
  currentLocationId: string,
  resource: LocationScopedResource<T> | undefined
): T | undefined => (
  isFreshLocationScopedResource(currentLocationId, resource)
    ? resource?.value
    : undefined
);
