export const parsePositiveRouteId = (value: string | number, label: string): number => {
  const parsedId = Number(value);
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    throw new Error(`Invalid ${label}`);
  }
  return parsedId;
};

export const parseRouteLocationId = (locationId: string): number => (
  parsePositiveRouteId(locationId, "location id")
);
