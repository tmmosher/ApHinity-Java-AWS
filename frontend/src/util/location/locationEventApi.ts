import {apiFetch} from "../common/apiFetch";
import {normalizeYearMonth} from "./dateUtility";
import type {
  CreateLocationServiceEventRequest,
  LocationServiceEvent
} from "../../types/Types";
import {
  apiErrorPayloadSchema,
  locationServiceEventListSchema,
  locationServiceEventSchema,
  serviceCalendarUploadResponseSchema
} from "./locationEventApiSchemas";

type ApiErrorPayload = ReturnType<typeof parseApiErrorPayload>;

const parsePositiveRouteId = (value: string | number, label: string): number => {
  const parsedId = Number(value);
  if (!Number.isFinite(parsedId) || parsedId <= 0) {
    throw new Error(`Invalid ${label}`);
  }
  return parsedId;
};

const parseRouteLocationId = (locationId: string): number => parsePositiveRouteId(locationId, "location id");

const parseRouteEventId = (eventId: number): number => parsePositiveRouteId(eventId, "event id");

const parseApiErrorPayload = (value: unknown) => {
  const parsed = apiErrorPayloadSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
};

const parseLocationServiceEvent = (value: unknown): LocationServiceEvent => {
  const parsed = locationServiceEventSchema.safeParse(value);
  if (!parsed.success) {
    throw new Error("Invalid service event response");
  }
  return parsed.data;
};

const parseLocationServiceEventList = (value: unknown): LocationServiceEvent[] => {
  const parsed = locationServiceEventListSchema.safeParse(value);
  if (!parsed.success) {
    throw new Error("Invalid service event response");
  }
  return parsed.data;
};

const parseServiceCalendarUploadResponse = (value: unknown): {importedCount: number} => {
  const parsed = serviceCalendarUploadResponseSchema.safeParse(value);
  if (!parsed.success) {
    throw new Error("Invalid service calendar upload response");
  }
  return parsed.data;
};

const throwLocationEventLoadError = async (response: Response): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  throw new Error("Unable to load service events");
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

const logLocationEventError = (
  operation: string,
  response: Response,
  errorPayload: ApiErrorPayload
): void => {
  console.warn(operation, {
    status: response.status,
    code: errorPayload?.code ?? null,
    message: errorPayload?.message ?? null,
    error: errorPayload?.error ?? null,
    path: errorPayload?.path ?? null
  });
};

const throwLocationEventMutationError = async (
  response: Response,
  action: "create" | "update" | "delete"
): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  logLocationEventError(`${action}LocationEvent failed`, response, errorPayload);
  throwAuthenticationOrSecurityError(response, errorPayload);

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  if (response.status === 403) {
    throw new Error("Insufficient permissions");
  }
  throw new Error(
    action === "create"
      ? "Unable to create service event"
      : action === "update"
        ? "Unable to update service event"
        : "Unable to delete service event"
  );
};

const throwLocationEventUploadError = async (response: Response): Promise<never> => {
  const errorPayload = parseApiErrorPayload(await response.json().catch(() => null));
  logLocationEventError("uploadLocationEventCalendar failed", response, errorPayload);
  throwAuthenticationOrSecurityError(response, errorPayload);

  if (errorPayload?.message) {
    throw new Error(errorPayload.message);
  }
  throw new Error("Unable to upload service calendar spreadsheet");
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

export const getLocationEventTemplateDownloadUrl = (host: string, locationId: string): string => {
  const parsedLocationId = parseRouteLocationId(locationId);
  return host + "/api/core/locations/" + parsedLocationId + "/events/template";
};

export const uploadLocationEventCalendarById = async (
  host: string,
  locationId: string,
  file: File
): Promise<{importedCount: number}> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const formData = new FormData();
  formData.set("file", file);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/events/calendar-upload", {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    await throwLocationEventUploadError(response);
  }

  return parseServiceCalendarUploadResponse(await response.json());
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

export const deleteLocationEventById = async (
  host: string,
  locationId: string,
  eventId: number
): Promise<void> => {
  const parsedLocationId = parseRouteLocationId(locationId);
  const parsedEventId = parseRouteEventId(eventId);

  const response = await apiFetch(host + "/api/core/locations/" + parsedLocationId + "/events/" + parsedEventId, {
    method: "DELETE"
  });

  if (!response.ok) {
    await throwLocationEventMutationError(response, "delete");
  }
};
