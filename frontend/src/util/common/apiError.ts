import {apiErrorPayloadSchema, type ApiErrorPayload} from "./apiSchemas";

export const parseApiErrorPayload = (value: unknown): ApiErrorPayload | null => {
  const parsed = apiErrorPayloadSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
};

export const throwAuthenticationOrSecurityError = (
  response: Response,
  errorPayload: ApiErrorPayload | null
): void => {
  if (
    errorPayload?.code === "authentication_required"
    || errorPayload?.code === "invalid_refresh_token"
    || errorPayload?.code === "missing_refresh_token"
  ) {
    throw new Error("Authentication required");
  }
  if (errorPayload?.code === "csrf_invalid") {
    throw new Error("CSRF invalid");
  }
  if (errorPayload?.code === "forbidden") {
    throw new Error("Insufficient permissions");
  }
  if (
    (response.status === 403 || errorPayload?.status === 403)
    && errorPayload?.error === "Forbidden"
  ) {
    throw new Error("Security token rejected");
  }
};
