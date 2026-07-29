import {apiFetch} from "../common/apiFetch";

export type PasswordChangeCredentials = {
  currentPassword: string;
  newPassword: string;
};

const errorMessage = async (response: Response): Promise<string> => {
  const body = await response.json().catch(() => null) as {message?: unknown} | null;
  return typeof body?.message === "string" && body.message.trim()
    ? body.message
    : "Unable to update password";
};

/** Sends an authenticated password change request. */
export const updateProfilePassword = async (
  apiHost: string,
  credentials: PasswordChangeCredentials
): Promise<void> => {
  const response = await apiFetch(apiHost + "/api/core/profile/password", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(credentials)
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
};
