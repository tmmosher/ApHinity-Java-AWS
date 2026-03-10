import {AccountRole, ManagedUserRole, ManagedUserRolePage} from "../../types/Types";
import {apiFetch} from "./apiFetch";

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const parseAccountRole = (value: unknown): AccountRole => {
  if (value === "admin" || value === "partner" || value === "client") {
    return value;
  }
  throw new Error("Invalid user role");
};

const parseManagedUserRole = (value: unknown): ManagedUserRole => {
  if (!isRecord(value)) {
    throw new Error("Invalid managed user response");
  }
  if (typeof value.id !== "number" || typeof value.email !== "string") {
    throw new Error("Invalid managed user shape");
  }

  return {
    id: value.id,
    name: typeof value.name === "string" ? value.name : "",
    email: value.email,
    role: parseAccountRole(value.role)
  };
};

const parseManagedUserRolePage = (value: unknown): ManagedUserRolePage => {
  if (!isRecord(value) || !Array.isArray(value.users)) {
    throw new Error("Invalid user role page response");
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
    users: value.users.map(parseManagedUserRole),
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
  size: number
): Promise<ManagedUserRolePage> => {
  const response = await apiFetch(host + "/api/core/admin/users?page=" + page + "&size=" + size, {
    method: "GET"
  });
  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Unable to load users"));
  }
  return parseManagedUserRolePage(await response.json());
};

export const updateManagedUserRole = async (
  host: string,
  userId: number,
  role: AccountRole
): Promise<ManagedUserRole> => {
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
  return parseManagedUserRole(await response.json());
};
