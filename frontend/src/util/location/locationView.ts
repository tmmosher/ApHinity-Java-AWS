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
  {view: "service-calendar", name: "Service Calendar", label: service_icon},
  {view: "gantt-chart", name: "Gantt Chart", label: gantt_icon},
  {view: "dashboard", name: "Dashboard", label: dashboard_icon}
];

export const createLocationViewActive = (
  currentView: Accessor<DashboardLocationView>,
  view: DashboardLocationView
): Accessor<boolean> => () => currentView() === view;

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

  if (normalizedPathname === basePath || normalizedPathname === `${basePath}/service-schedule`) {
    return "service-calendar";
  }
  if (normalizedPathname === `${basePath}/gantt-chart`) {
    return "gantt-chart";
  }
  if (normalizedPathname === `${basePath}/dashboard`) {
    return "dashboard";
  }
  return "service-calendar";
};

export const getLocationViewHref = (locationId: string, view: DashboardLocationView): string => {
  if (view === "service-calendar") {
    return `/dashboard/locations/${locationId}`;
  }
  return `/dashboard/locations/${locationId}/${view}`;
};

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
