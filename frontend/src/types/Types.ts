export interface ActionResult {
    ok: boolean;
    message?: string;
    code?: string;
}

export interface Profile {
    name: string;
    email: string;
    verified: boolean;
}