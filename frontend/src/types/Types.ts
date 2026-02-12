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
