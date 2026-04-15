import {apiFetch} from "../common/apiFetch";
import type {
  CreateLocationGanttTaskRequest,
  LocationGanttTask
} from "../../types/Types";
import {
  apiErrorPayloadSchema,
  locationGanttTaskListSchema,
  locationGanttTaskSchema
} from "./locationGanttTaskApiSchemas";

type ApiErrorPayload = ReturnType<typeof parseApiErrorPayload>;

const parsePositiveRouteId = (value: string | number, label: string): number => {
  const parsedId = Number(value);
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    throw new Error(`Invalid ${label}`);
  }
  return parsedId;
};

const parseRouteLocationId = (locationId: string): number => parsePositiveRouteId(locationId, "location id");

const parseRouteTaskId = (taskId: number): number => parsePositiveRouteId(taskId, "task id");

const parseApiErrorPayload = (value: unknown) => {
  const parsed = apiErrorPayloadSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
};

const parseLocationGanttTask = (value: unknown): LocationGanttTask => {
  const parsed = locationGanttTaskSchema.safeParse(value);
  if (!parsed.success) {
    throw new Error("Invalid gantt task response");
  }
  return parsed.data;
};

const parseLocationGanttTaskList = (value: unknown): LocationGanttTask[] => {
  const parsed = locationGanttTaskListSchema.safeParse(value);
  if (!parsed.success) {
    throw new Error("Invalid gantt task response");
  }
  return parsed.data;
};

const throwLocationGanttTaskLoadError = async (response: Response): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  throw new Error("Unable to load gantt tasks");
};

const throwAuthenticationOrSecurityError = (
  response: Response,
  errorPayload: ApiErrorPayload
): void => {
  if (
    errorPayload?.code === "authentication_required"
    || errorPayload?.code === "invalid_refresh_token"
    || errorPayload?.code === "missing_refresh_token"
  ) {
    throw new Error("Authentication required");
  }
  if (errorPayload?.code === "csrf_invalid") {
    throw new Error("CSRF invalid");
  }
  if (errorPayload?.code === "forbidden") {
    throw new Error("Insufficient permissions");
  }
  if (
    (response.status === 403 || errorPayload?.status === 403)
    && errorPayload?.error === "Forbidden"
  ) {
    throw new Error("Security token rejected");
  }
};

const throwLocationGanttTaskMutationError = async (response: Response, action: "create" | "update"): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  throwAuthenticationOrSecurityError(response, errorPayload);

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  if (response.status === 403) {
    throw new Error("Insufficient permissions");
  }
  throw new Error(action === "create" ? "Unable to create gantt task" : "Unable to update gantt task");
};

const throwLocationGanttTaskDeletionError = async (response: Response): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  throwAuthenticationOrSecurityError(response, errorPayload);

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  if (response.status === 403) {
    throw new Error("Insufficient permissions");
  }
  throw new Error("Unable to delete gantt task");
};

export const fetchLocationGanttTasksById = async (
  host: string,
  locationId: string,
  searchTerm?: string
): Promise<LocationGanttTask[]> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const queryString = searchTerm && searchTerm.trim()
    ? "?search=" + encodeURIComponent(searchTerm.trim())
    : "";

  const response = await apiFetch(
    host + "/api/core/locations/" + parsedLocationId + "/gantt-tasks" + queryString,
    {
      method: "GET"
    }
  );

  if (!response.ok) {
    await throwLocationGanttTaskLoadError(response);
  }

  return parseLocationGanttTaskList(await response.json());
};

export const createLocationGanttTaskById = async (
  host: string,
  locationId: string,
  request: CreateLocationGanttTaskRequest
): Promise<LocationGanttTask> => {
  const parsedLocationId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/gantt-tasks", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    await throwLocationGanttTaskMutationError(response, "create");
  }

  return parseLocationGanttTask(await response.json());
};

export const updateLocationGanttTaskById = async (
  host: string,
  locationId: string,
  taskId: number,
  request: CreateLocationGanttTaskRequest
): Promise<LocationGanttTask> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedTaskId = parseRouteTaskId(taskId);

  const response = await apiFetch(
    host + "/api/core/locations/" + parsedLocationId + "/gantt-tasks/" + parsedTaskId,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(request)
    }
  );

  if (!response.ok) {
    await throwLocationGanttTaskMutationError(response, "update");
  }

  return parseLocationGanttTask(await response.json());
};

export const deleteLocationGanttTaskById = async (
  host: string,
  locationId: string,
  taskId: number
): Promise<void> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedTaskId = parseRouteTaskId(taskId);

  const response = await apiFetch(
    host + "/api/core/locations/" + parsedLocationId + "/gantt-tasks/" + parsedTaskId,
    {
      method: "DELETE"
    }
  );

  if (!response.ok) {
    await throwLocationGanttTaskDeletionError(response);
  }
};
