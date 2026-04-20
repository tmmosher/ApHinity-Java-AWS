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

/**
 * This wraps the LocationMembership interface so I can make a queue for partners to delete.
 */
export interface LocationMembershipWithStatus {
    membership: LocationMembership;
    active: boolean; // whether a membership is queued to be deleted
}

export interface LocationGraph {
    id: number;
    name: string;
    data: Record<string, unknown>[];
    layout?: Record<string, unknown> | null;
    config?: Record<string, unknown> | null;
    style?: Record<string, unknown> | null;
    createdAt: string;
    updatedAt: string;
}

export interface LocationGraphUpdate {
    graphId: number;
    data: Record<string, unknown>[];
    layout?: Record<string, unknown> | null;
    config?: Record<string, unknown> | null;
    style?: Record<string, unknown> | null;
    expectedUpdatedAt?: string | null;
}

export interface LocationGraphRenameResult {
    graphId: number;
    name: string;
    updatedAt: string;
}

export type LocationGraphType = "pie" | "indicator" | "bar" | "scatter";

export type ServiceEventResponsibility = "client" | "partner";

export type ServiceEventStatus = "upcoming" | "current" | "overdue" | "completed";

export interface LocationServiceEvent {
    id: number;
    title: string;
    responsibility: ServiceEventResponsibility;
    date: string;
    time: string;
    endDate: string;
    endTime: string;
    description: string | null;
    status: ServiceEventStatus;
    isCorrectiveAction?: boolean;
    correctiveActionSourceEventId?: number | null;
    correctiveActionSourceEventTitle?: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateLocationServiceEventRequest {
    title: string;
    responsibility: ServiceEventResponsibility;
    date: string;
    time: string;
    endDate: string;
    endTime: string;
    description: string | null;
    status: ServiceEventStatus;
}

export interface LocationGanttTask {
    id: number;
    title: string;
    startDate: string;
    endDate: string;
    description: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateLocationGanttTaskRequest {
    title: string;
    startDate: string;
    endDate: string;
    description: string | null;
}

export interface ManagedUser {
  id: number;
  name: string;
  email: string;
  role: AccountRole;
  pendingDeletion: boolean;
}

export interface ManagedUserPage {
    users: ManagedUser[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export type ManagedUserRole = ManagedUser;

export type ManagedUserRolePage = ManagedUserPage;
