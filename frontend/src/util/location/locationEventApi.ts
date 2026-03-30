import {apiFetch} from "../common/apiFetch";
import {normalizeYearMonth} from "./dateUtility";
import type {
  CreateLocationServiceEventRequest,
  LocationServiceEvent,
  ServiceEventResponsibility,
  ServiceEventStatus
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

const parsePositiveRouteId = (value: string | number, label: string): number => {
  const parsedId = Number(value);
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    throw new Error(`Invalid ${label}`);
  }
  return parsedId;
};

const parseRouteLocationId = (locationId: string): number => parsePositiveRouteId(locationId, "location id");

const parseRouteEventId = (eventId: number): number => parsePositiveRouteId(eventId, "event id");

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

const isServiceEventResponsibility = (value: unknown): value is ServiceEventResponsibility =>
  value === "client" || value === "partner";

const isServiceEventStatus = (value: unknown): value is ServiceEventStatus =>
  value === "upcoming" || value === "current" || value === "overdue" || value === "completed";

const parseLocationServiceEvent = (value: unknown): LocationServiceEvent => {
  if (!isRecord(value)) {
    throw new Error("Invalid service event response");
  }
  if (
    typeof value.id !== "number"
    || typeof value.title !== "string"
    || !isServiceEventResponsibility(value.responsibility)
    || typeof value.date !== "string"
    || typeof value.time !== "string"
    || typeof value.endDate !== "string"
    || typeof value.endTime !== "string"
    || (value.description !== null && value.description !== undefined && typeof value.description !== "string")
    || !isServiceEventStatus(value.status)
    || typeof value.createdAt !== "string"
    || typeof value.updatedAt !== "string"
  ) {
    throw new Error("Invalid service event response");
  }
  return {
    id: value.id,
    title: value.title,
    responsibility: value.responsibility,
    date: value.date,
    time: value.time,
    endDate: value.endDate,
    endTime: value.endTime,
    description: value.description ?? null,
    status: value.status,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt
  };
};

const parseLocationServiceEventList = (value: unknown): LocationServiceEvent[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid service event response");
  }
  return value.map(parseLocationServiceEvent);
};

const throwLocationEventLoadError = async (response: Response): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  throw new Error("Unable to load service events");
};

const throwLocationEventMutationError = async (
  response: Response,
  action: "create" | "update"
): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  console.warn(`${action}LocationEvent failed`, {
    status: response.status,
    code: errorPayload?.code ?? null,
    message: errorPayload?.message ?? null,
    error: errorPayload?.error ?? null,
    path: errorPayload?.path ?? null
  });

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
  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  if (response.status === 403) {
    throw new Error("Insufficient permissions");
  }
  throw new Error(action === "create" ? "Unable to create service event" : "Unable to update service event");
};

export const fetchLocationEventsById = async (
  host: string,
  locationId: string,
  month: string
): Promise<LocationServiceEvent[]> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const normalizedMonth = normalizeYearMonth(month);

  const response = await apiFetch(
    host + "/api/core/locations/" + parsedLocationId + "/events?month=" + encodeURIComponent(normalizedMonth),
    {
      method: "GET"
    }
  );

  if (!response.ok) {
    await throwLocationEventLoadError(response);
  }

  return parseLocationServiceEventList(await response.json());
};

export const createLocationEventById = async (
  host: string,
  locationId: string,
  request: CreateLocationServiceEventRequest
): Promise<LocationServiceEvent> => {
  const parsedLocationId = parseRouteLocationId(locationId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/events", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    await throwLocationEventMutationError(response, "create");
  }

  return parseLocationServiceEvent(await response.json());
};

export const updateLocationEventById = async (
  host: string,
  locationId: string,
  eventId: number,
  request: CreateLocationServiceEventRequest
): Promise<LocationServiceEvent> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedEventId = parseRouteEventId(eventId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/events/" + parsedEventId, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    await throwLocationEventMutationError(response, "update");
  }

  return parseLocationServiceEvent(await response.json());
};
