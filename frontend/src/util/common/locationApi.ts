import {LocationSummary} from "../../types/Types";
import {parseLocationSummary} from "./coreApi";
import {apiFetch} from "./apiFetch";
import {parseOptionalWorkOrderEmail} from "./apiSchemas";

const extractApiErrorMessage = async (response: Response, fallback: string): Promise<string> => {
  const payload = await response.json().catch(() => null) as {message?: unknown} | null;
  if (payload && typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }
  return fallback;
};

const normalizeLocationName = (name: string): string => {
  const normalized = name.trim();
  if (!normalized) {
    throw new Error("Location name is required");
  }
  return normalized;
};

export const createLocation = async (
  host: string,
  name: string
): Promise<LocationSummary> => {
  const normalizedName = normalizeLocationName(name);
  const response = await apiFetch(host + "/api/core/locations", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      name: normalizedName
    })
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to create location."));
  }
  return parseLocationSummary(await response.json());
};

export const renameLocation = async (
  host: string,
  locationId: number,
  name: string
): Promise<LocationSummary> => {
  const normalizedName = normalizeLocationName(name);
  const response = await apiFetch(host + "/api/core/locations/" + locationId, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      name: normalizedName
    })
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update location name."));
  }
  return parseLocationSummary(await response.json());
};

export const updateLocationWorkOrderEmail = async (
  host: string,
  locationId: number,
  workOrderEmail: string | null
): Promise<LocationSummary> => {
  const response = await apiFetch(host + "/api/core/locations/" + locationId + "/work-order-email", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      workOrderEmail: parseOptionalWorkOrderEmail(workOrderEmail)
    })
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update location work order email."));
  }
  return parseLocationSummary(await response.json());
};

export const subscribeToLocationAlerts = async (
  host: string,
  locationId: number
): Promise<LocationSummary> => {
  const response = await apiFetch(host + "/api/core/locations/" + locationId + "/alerts/subscription", {
    method: "PUT"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update location alert subscription."));
  }
  return parseLocationSummary(await response.json());
};

export const unsubscribeFromLocationAlerts = async (
  host: string,
  locationId: number
): Promise<LocationSummary> => {
  const response = await apiFetch(host + "/api/core/locations/" + locationId + "/alerts/subscription", {
    method: "DELETE"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update location alert subscription."));
  }
  return parseLocationSummary(await response.json());
};

export const uploadLocationThumbnail = async (
  host: string,
  locationId: number,
  file: File
): Promise<LocationSummary> => {
  const formData = new FormData();
  formData.set("file", file);

  const response = await apiFetch(host + "/api/core/locations/" + locationId + "/thumbnail", {
    method: "POST",
    body: formData
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update location thumbnail"));
  }
  return parseLocationSummary(await response.json());
};
