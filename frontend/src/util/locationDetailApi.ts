import {parseLocationGraphList, parseLocationSummary} from "./coreApi";
import {apiFetch} from "./apiFetch";
import {LocationGraph, LocationGraphUpdate, LocationSummary} from "../types/Types";

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

type ApiErrorPayload = {
  code?: string;
  message?: string;
  error?: string;
  status?: number;
  path?: string;
};

const parseApiErrorPayload = (value: unknown): ApiErrorPayload | null => {
  if (!isRecord(value)) {
    return null;
  }
  return {
    code: typeof value.code === "string" ? value.code : undefined,
    message: typeof value.message === "string" ? value.message : undefined,
    error: typeof value.error === "string" ? value.error : undefined,
    status: typeof value.status === "number" ? value.status : undefined,
    path: typeof value.path === "string" ? value.path : undefined
  };
};

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

/**
 * Persists edited graph payloads for a location.
 *
 * Endpoint: `PUT /api/core/locations/{locationId}/graphs`
 * Body: `{ graphs: [{ graphId, data, layout, config, style, expectedUpdatedAt? }] }`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @param graphUpdates Graph payloads to persist.
 * @throws {Error} When id is invalid or request fails.
 */
export const saveLocationGraphsById = async (
  host: string,
  locationId: string,
  graphUpdates: LocationGraphUpdate[]
): Promise<void> => {
  const parsedId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedId + "/graphs", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      graphs: graphUpdates
    })
  });

  if (!response.ok) {
    const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
    console.warn("saveLocationGraphsById failed", {
      status: response.status,
      code: errorPayload?.code ?? null,
      message: errorPayload?.message ?? null,
      error: errorPayload?.error ?? null,
      path: errorPayload?.path ?? null
    });

    try {
      if (errorPayload?.code) {
        if (errorPayload.code === "graph_update_conflict") {
          throw new Error("Graph update conflict");
        }
        if (errorPayload.code === "csrf_invalid") {
          throw new Error("CSRF invalid");
        }
        if (errorPayload.code === "forbidden") {
          throw new Error("Insufficient permissions");
        }
        if (
          errorPayload.code === "authentication_required"
          || errorPayload.code === "invalid_refresh_token"
          || errorPayload.code === "missing_refresh_token"
        ) {
          throw new Error("Authentication required");
        }
      }
      if (
        (response.status === 403 || errorPayload?.status === 403)
        && errorPayload?.error === "Forbidden"
      ) {
        throw new Error("Security token rejected");
      }
    } catch (error) {
      if (
        error instanceof Error &&
        (
          error.message === "Graph update conflict" ||
          error.message === "CSRF invalid" ||
          error.message === "Insufficient permissions" ||
          error.message === "Authentication required" ||
          error.message === "Security token rejected"
        )
      ) {
        throw error;
      }
      // Continue to generic error when response body is not parseable.
    }
    throw new Error("Unable to save location graphs");
  }
};
