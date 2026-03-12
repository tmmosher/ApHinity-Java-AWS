import {AccountRole, ManagedUser, ManagedUserPage} from "../../types/Types";
import {apiFetch} from "./apiFetch";

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const parseAccountRole = (value: unknown): AccountRole => {
  if (value === "admin" || value === "partner" || value === "client") {
    return value;
  }
  throw new Error("Invalid user role");
};

const parseManagedUser = (value: unknown): ManagedUser => {
  if (!isRecord(value)) {
    throw new Error("Invalid managed user response");
  }
  if (
    typeof value.id !== "number"
    || typeof value.email !== "string"
    || typeof value.pendingDeletion !== "boolean"
  ) {
    throw new Error("Invalid managed user shape");
  }

  return {
    id: value.id,
    name: typeof value.name === "string" ? value.name : "",
    email: value.email,
    role: parseAccountRole(value.role),
    pendingDeletion: value.pendingDeletion
  };
};

const parseManagedUserPage = (value: unknown): ManagedUserPage => {
  if (!isRecord(value) || !Array.isArray(value.users)) {
    throw new Error("Invalid managed user page response");
  }
  if (
    typeof value.page !== "number"
    || typeof value.size !== "number"
    || typeof value.totalElements !== "number"
    || typeof value.totalPages !== "number"
  ) {
    throw new Error("Invalid user role page metadata");
  }

  return {
    users: value.users.map(parseManagedUser),
    page: value.page,
    size: value.size,
    totalElements: value.totalElements,
    totalPages: value.totalPages
  };
};

const extractApiErrorMessage = async (response: Response, fallback: string): Promise<string> => {
  const payload = await response.json().catch(() => null) as {message?: unknown} | null;
  if (payload && typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }
  return fallback;
};

export const fetchManagedUsers = async (
  host: string,
  page: number,
  size: number,
  query = ""
): Promise<ManagedUserPage> => {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });
  const normalizedQuery = query.trim();
  if (normalizedQuery) {
    params.set("query", normalizedQuery);
  }
  const response = await apiFetch(host + "/api/core/admin/users?" + params.toString(), {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to load users"));
  }
  return parseManagedUserPage(await response.json());
};

export const updateManagedUserRole = async (
  host: string,
  userId: number,
  role: AccountRole
): Promise<ManagedUser> => {
  const response = await apiFetch(host + "/api/core/admin/users/" + userId + "/role", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      role
    })
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to update user role."));
  }
  return parseManagedUser(await response.json());
};

export const markManagedUserForDeletion = async (
  host: string,
  userId: number
): Promise<ManagedUser> => {
  const response = await apiFetch(host + "/api/core/admin/users/" + userId + "/deletion", {
    method: "PUT"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to queue user deletion."));
  }
  return parseManagedUser(await response.json());
};

export const restoreManagedUserDeletion = async (
  host: string,
  userId: number
): Promise<ManagedUser> => {
  const response = await apiFetch(host + "/api/core/admin/users/" + userId + "/deletion", {
    method: "DELETE"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to restore user."));
  }
  return parseManagedUser(await response.json());
};
