import {AccountRole} from "../types/Types";

/**
 * Partners cannot self-edit account email in the profile UI.
 */
export const canEditProfileEmail = (role: AccountRole | undefined): boolean => role !== "partner";
