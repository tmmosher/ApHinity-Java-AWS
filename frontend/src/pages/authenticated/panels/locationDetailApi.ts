import {parseLocationGraphList, parseLocationSummary} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";
import {LocationGraph, LocationSummary} from "../../../types/Types";

export const parseRouteLocationId = (locationId: string): number => {
  const parsedId = Number(locationId);
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    throw new Error("Invalid location id");
  }
  return parsedId;
};

/**
 * Loads a single location by id.
 *
 * Endpoint: `GET /api/core/locations/{locationId}`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @returns Parsed location summary.
 * @throws {Error} When id is invalid or request fails.
 */
export const fetchLocationById = async (host: string, locationId: string): Promise<LocationSummary> => {
  const parsedId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedId, {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error("Unable to load location");
  }
  return parseLocationSummary(await response.json());
};

/**
 * Loads all graphs assigned to the selected location.
 *
 * Endpoint: `GET /api/core/locations/{locationId}/graphs`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @returns Parsed graph payloads.
 * @throws {Error} When id is invalid or request fails.
 */
export const fetchLocationGraphsById = async (host: string, locationId: string): Promise<LocationGraph[]> => {
  const parsedId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedId + "/graphs", {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error("Unable to load location graphs");
  }
  return parseLocationGraphList(await response.json());
};
