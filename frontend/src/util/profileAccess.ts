import {AccountRole} from "../types/Types";

/**
 * Partners cannot self-edit account email in the profile UI.
 */
export const canEditProfileEmail = (role: AccountRole | undefined): boolean => role !== "partner";

/**
 * Location graph editing is only available to partner and admin accounts.
 */
export const canEditLocationGraphs = (role: AccountRole | undefined): boolean =>
  role === "admin" || role === "partner";
