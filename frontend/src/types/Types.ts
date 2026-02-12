export interface ActionResult {
    ok: boolean;
    message?: string;
    code?: string;
}

export type AccountRole = "admin" | "partner" | "client";

export interface Profile {
    name: string;
    email: string;
    verified: boolean;
    role: AccountRole;
}

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