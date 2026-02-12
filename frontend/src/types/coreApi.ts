export type InviteStatus = "pending" | "accepted" | "revoked" | "expired";
export type LocationMemberRole = "admin" | "partner" | "client";

export interface LocationSummary {
  id: number;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface ActiveInvite {
  id: number;
  locationId: number;
  locationName: string | null;
  invitedEmail: string;
  invitedByUserId: number | null;
  status: InviteStatus;
  expiresAt: string;
  createdAt: string;
  acceptedAt: string | null;
  acceptedUserId: number | null;
  revokedAt: string | null;
}

export interface LocationMembership {
  locationId: number;
  userId: number;
  userEmail: string | null;
  userRole: LocationMemberRole;
  createdAt: string;
}

const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === "object";

const isInviteStatus = (value: unknown): value is InviteStatus =>
  value === "pending" || value === "accepted" || value === "revoked" || value === "expired";

const isLocationMemberRole = (value: unknown): value is LocationMemberRole =>
  value === "admin" || value === "partner" || value === "client";

export const parseLocationSummary = (value: unknown): LocationSummary => {
  if (!isObject(value)) {
    throw new Error("Invalid location response");
  }
  if (
    typeof value.id !== "number" ||
    typeof value.name !== "string" ||
    typeof value.createdAt !== "string" ||
    typeof value.updatedAt !== "string"
  ) {
    throw new Error("Invalid location shape");
  }
  return {
    id: value.id,
    name: value.name,
    createdAt: value.createdAt,
    updatedAt: value.updatedAt
  };
};

export const parseLocationList = (value: unknown): LocationSummary[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid location list response");
  }
  return value.map(parseLocationSummary);
};

export const parseActiveInvite = (value: unknown): ActiveInvite => {
  if (!isObject(value)) {
    throw new Error("Invalid invite response");
  }
  if (
    typeof value.id !== "number" ||
    typeof value.locationId !== "number" ||
    (value.locationName !== null && typeof value.locationName !== "string") ||
    typeof value.invitedEmail !== "string" ||
    (value.invitedByUserId !== null && typeof value.invitedByUserId !== "number") ||
    !isInviteStatus(value.status) ||
    typeof value.expiresAt !== "string" ||
    typeof value.createdAt !== "string" ||
    (value.acceptedAt !== null && typeof value.acceptedAt !== "string") ||
    (value.acceptedUserId !== null && typeof value.acceptedUserId !== "number") ||
    (value.revokedAt !== null && typeof value.revokedAt !== "string")
  ) {
    throw new Error("Invalid invite shape");
  }
  return {
    id: value.id,
    locationId: value.locationId,
    locationName: value.locationName,
    invitedEmail: value.invitedEmail,
    invitedByUserId: value.invitedByUserId,
    status: value.status,
    expiresAt: value.expiresAt,
    createdAt: value.createdAt,
    acceptedAt: value.acceptedAt,
    acceptedUserId: value.acceptedUserId,
    revokedAt: value.revokedAt
  };
};

export const parseActiveInviteList = (value: unknown): ActiveInvite[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid invite list response");
  }
  return value.map(parseActiveInvite);
};

export const parseLocationMembership = (value: unknown): LocationMembership => {
  if (!isObject(value)) {
    throw new Error("Invalid membership response");
  }
  if (
    typeof value.locationId !== "number" ||
    typeof value.userId !== "number" ||
    (value.userEmail !== null && typeof value.userEmail !== "string") ||
    !isLocationMemberRole(value.userRole) ||
    typeof value.createdAt !== "string"
  ) {
    throw new Error("Invalid membership shape");
  }
  return {
    locationId: value.locationId,
    userId: value.userId,
    userEmail: value.userEmail,
    userRole: value.userRole,
    createdAt: value.createdAt
  };
};

export const parseLocationMembershipList = (value: unknown): LocationMembership[] => {
  if (!Array.isArray(value)) {
    throw new Error("Invalid membership list response");
  }
  return value.map(parseLocationMembership);
};
