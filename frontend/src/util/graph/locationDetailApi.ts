import {parseLocationGraph, parseLocationGraphList, parseLocationSummary} from "../common/coreApi";
import {apiFetch} from "../common/apiFetch";
import {
  LocationGraph,
  LocationGraphRenameResult,
  LocationGraphType,
  LocationGraphUpdate,
  LocationSummary
} from "../../types/Types";

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

export const parseGraphId = (graphId: number): number => {
  if (!Number.isFinite(graphId) || graphId <= 0) {
    throw new Error("Invalid graph id");
  }
  return graphId;
};

const parseLocationGraphRenameResult = (value: unknown): LocationGraphRenameResult => {
  if (!isRecord(value)) {
    throw new Error("Invalid graph rename response");
  }
  if (
    typeof value.graphId !== "number"
    || typeof value.name !== "string"
    || typeof value.updatedAt !== "string"
  ) {
    throw new Error("Invalid graph rename shape");
  }
  return {
    graphId: value.graphId,
    name: value.name,
    updatedAt: value.updatedAt
  };
};

type CreateLocationGraphRequest = {
  sectionId?: number;
  createNewSection?: boolean;
  graphType: LocationGraphType;
};

const throwGraphMutationError = async (
  response: Response,
  operation: "create" | "save" | "rename" | "delete"
): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  console.warn(`${operation}LocationGraph failed`, {
    status: response.status,
    code: errorPayload?.code ?? null,
    message: errorPayload?.message ?? null,
    error: errorPayload?.error ?? null,
    path: errorPayload?.path ?? null
  });
// using throw / catch for flow control here is pretty stupid imo but it works
  try {
    if (errorPayload?.code) {
      errorCheck(errorPayload.code);
    }
    if (
      (response.status === 403 || errorPayload?.status === 403)
      && errorPayload?.error === "Forbidden"
    ) {
      throw new Error("Security token rejected");
    }
  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
  }

  if (operation === "rename") {
    throw new Error("Unable to rename graph");
  }
  if (operation === "create") {
    throw new Error("Unable to create graph");
  }
  if (operation === "delete") {
    throw new Error("Unable to delete graph");
  }
  throw new Error("Unable to save location graphs");
};

const errorCheck = (code: string) => {
    if (code === "location_section_not_found") {
        throw new Error("Location section not found");
    }
    if (code === "location_graph_not_found") {
        throw new Error("Location graph not found");
    }
    if (code === "graph_type_invalid") {
        throw new Error("Graph type is invalid");
    }
    if (code === "graph_update_conflict") {
        throw new Error("Graph update conflict");
    }
    if (code === "graph_name_required") {
        throw new Error("Graph name is required");
    }
    if (code === "csrf_invalid") {
        throw new Error("CSRF invalid");
    }
    if (code === "forbidden") {
        throw new Error("Insufficient permissions");
    }
    if (
        code === "authentication_required"
        || code === "invalid_refresh_token"
        || code === "missing_refresh_token"
    ) {
        throw new Error("Authentication required");
    }
}

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
    await throwGraphMutationError(response, "save");
  }
};

/**
 * Creates a new graph for a location and assigns it to a dashboard section.
 *
 * Endpoint: `POST /api/core/locations/{locationId}/graphs`
 * Body: `{ graphType, sectionId? , createNewSection? }`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @param request Target section and graph type.
 * @returns Created graph payload.
 * @throws {Error} When id is invalid or request fails.
 */
export const createLocationGraphById = async (
  host: string,
  locationId: string,
  request: CreateLocationGraphRequest
): Promise<LocationGraph> => {
  const parsedLocationId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/graphs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    await throwGraphMutationError(response, "create");
  }

  return parseLocationGraph(await response.json());
};

/**
 * Persists a graph name change through the dedicated rename endpoint.
 *
 * Endpoint: `PUT /api/core/locations/{locationId}/graphs/{graphId}/name`
 * Body: `{ name }`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @param graphId Graph id to rename.
 * @param name Next graph display name.
 * @returns Updated graph rename metadata.
 * @throws {Error} When ids are invalid or the request fails.
 */
export const renameLocationGraphById = async (
  host: string,
  locationId: string,
  graphId: number,
  name: string
): Promise<LocationGraphRenameResult> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedGraphId = parseGraphId(graphId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/graphs/" + parsedGraphId + "/name", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({name})
  });

  if (!response.ok) {
    await throwGraphMutationError(response, "rename");
  }

  return parseLocationGraphRenameResult(await response.json());
};

/**
 * Deletes a graph assigned to a location.
 *
 * Endpoint: `DELETE /api/core/locations/{locationId}/graphs/{graphId}`
 *
 * @param host API host base URL.
 * @param locationId Location id from route params.
 * @param graphId Graph id to delete.
 * @throws {Error} When ids are invalid or the request fails.
 */
export const deleteLocationGraphById = async (
  host: string,
  locationId: string,
  graphId: number
): Promise<void> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedGraphId = parseGraphId(graphId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/graphs/" + parsedGraphId, {
    method: "DELETE"
  });

  if (!response.ok) {
    await throwGraphMutationError(response, "delete");
  }
};
