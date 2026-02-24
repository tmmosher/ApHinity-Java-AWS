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

export interface LocationSectionLayout {
    section_id: number;
    graph_ids: number[];
}

export interface LocationSectionLayoutConfig {
    sections: LocationSectionLayout[];
}

export interface LocationSummary {
    id: number;
    name: string;
    createdAt: string;
    updatedAt: string;
    sectionLayout: LocationSectionLayoutConfig;
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
    createdAt: string;
}

export interface LocationGraphPayload {
    data: Record<string, unknown>[];
    layout?: Record<string, unknown> | null;
    config?: Record<string, unknown> | null;
}

export interface LocationGraph {
    id: number;
    name: string;
    data: LocationGraphPayload;
    createdAt: string;
    updatedAt: string;
}
