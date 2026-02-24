import {parseActiveInviteList, parseLocationList} from "../types/coreApi";
import {ActiveInvite, LocationSummary} from "../types/Types";
import {apiFetch} from "./apiFetch";

const extractApiErrorMessage = async (response: Response, fallback: string): Promise<string> => {
  const payload = await response.json().catch(() => null) as {message?: unknown} | null;
  if (payload && typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }
  return fallback;
};

/**
 * Loads inviteable locations for the authenticated elevated user.
 *
 * Endpoint: `GET /api/core/location-invites/locations`
 */
export const fetchInviteableLocations = async (host: string): Promise<LocationSummary[]> => {
  const response = await apiFetch(host + "/api/core/location-invites/locations", {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error("Unable to load locations");
  }
  return parseLocationList(await response.json());
};

/**
 * Creates a location invite for the selected location/email pair.
 *
 * Endpoint: `POST /api/core/location-invites`
 */
export const createLocationInvite = async (
  host: string,
  locationId: number,
  invitedEmail: string
): Promise<void> => {
  if (!Number.isFinite(locationId) || locationId <= 0) {
    throw new Error("Select a location first.");
  }
  const normalizedEmail = invitedEmail.trim().toLowerCase();
  if (!normalizedEmail) {
    throw new Error("Invite email is required.");
  }

  const response = await apiFetch(host + "/api/core/location-invites", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      locationId,
      invitedEmail: normalizedEmail
    })
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to create invite."));
  }
};

/**
 * Retrieves pending invites for the signed-in user's email.
 *
 * Endpoint: `GET /api/core/location-invites/active`
 */
export const fetchActiveInvites = async (host: string): Promise<ActiveInvite[]> => {
  const response = await apiFetch(host + "/api/core/location-invites/active", {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error("Unable to load invites");
  }
  return parseActiveInviteList(await response.json());
};

/**
 * Accepts or declines a pending invite.
 *
 * Endpoint: `POST /api/core/location-invites/{inviteId}/{action}`
 */
export const processLocationInvite = async (
  host: string,
  inviteId: number,
  action: "accept" | "decline"
): Promise<void> => {
  const response = await apiFetch(host + "/api/core/location-invites/" + inviteId + "/" + action, {
    method: "POST"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update invite."));
  }
};
