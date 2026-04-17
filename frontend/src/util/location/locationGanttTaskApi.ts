import {apiFetch} from "../common/apiFetch";
import {parseApiErrorPayload, throwAuthenticationOrSecurityError} from "../common/apiError";
import {parsePositiveRouteId, parseRouteLocationId} from "../common/routeParams";
import type {
  CreateLocationGanttTaskRequest,
  LocationGanttTask
} from "../../types/Types";
import {
  locationGanttTaskListSchema,
  locationGanttTaskSchema
} from "./locationGanttTaskApiSchemas";

const parseRouteTaskId = (taskId: number): number => parsePositiveRouteId(taskId, "task id");

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

const throwLocationGanttTaskMutationError = async (
  response: Response,
  action: "create" | "update" | "bulk-create"
): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  throwAuthenticationOrSecurityError(response, errorPayload);

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  if (response.status === 403) {
    throw new Error("Insufficient permissions");
  }
  if (action === "create") {
    throw new Error("Unable to create gantt task");
  }
  if (action === "bulk-create") {
    throw new Error("Unable to import gantt tasks");
  }
  throw new Error("Unable to update gantt task");
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

export const createLocationGanttTasksBulkById = async (
  host: string,
  locationId: string,
  requests: readonly CreateLocationGanttTaskRequest[]
): Promise<LocationGanttTask[]> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  if (requests.length === 0) {
    return [];
  }

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/gantt-tasks/bulk", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(requests)
  });

  if (!response.ok) {
    await throwLocationGanttTaskMutationError(response, "bulk-create");
  }

  return parseLocationGanttTaskList(await response.json());
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
