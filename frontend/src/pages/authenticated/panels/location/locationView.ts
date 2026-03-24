export type DashboardLocationView = "service-schedule" | "gantt-chart" | "dashboard";

export type LocationScopedResource<T> = {
  locationId: string;
  value: T;
};

export const dashboardLocationViews: Array<{
  view: DashboardLocationView;
  label: string;
}> = [
  {view: "service-schedule", label: "Service Schedule"},
  {view: "gantt-chart", label: "Gantt Chart"},
  {view: "dashboard", label: "Dashboard"}
];

export const normalizeLocationPathname = (pathname: string): string => pathname.replace(/\/+$/, "") || "/";

export const getLocationViewFromPathname = (pathname: string, locationId: string): DashboardLocationView => {
  const normalizedPathname = normalizeLocationPathname(pathname);
  const basePath = `/dashboard/locations/${locationId}`;

  if (normalizedPathname === basePath || normalizedPathname === `${basePath}/service-schedule`) {
    return "service-schedule";
  }
  if (normalizedPathname === `${basePath}/gantt-chart`) {
    return "gantt-chart";
  }
  if (normalizedPathname === `${basePath}/dashboard`) {
    return "dashboard";
  }
  return "service-schedule";
};

export const getLocationViewHref = (locationId: string, view: DashboardLocationView): string => {
  if (view === "service-schedule") {
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
