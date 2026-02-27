const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);
const CSRF_INVALID_ERROR_CODE = "csrf_invalid";
let csrfPrimeInFlight: Promise<void> | null = null;

/**
 * Reads a cookie value by name from `document.cookie`.
 *
 * @param name Cookie name to resolve.
 * @returns The decoded cookie value when found, otherwise `null`.
 */
const readCookie = (name: string): string | null => {
  const prefix = name + "=";
  const cookies = document.cookie.split(";");
  for (const rawCookie of cookies) {
    const cookie = rawCookie.trim();
    if (!cookie.startsWith(prefix)) {
      continue;
    }
    const value = cookie.substring(prefix.length);
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }
  return null;
};

/**
 * Sends an API request with project defaults.
 *
 * - Includes cookies by default (`credentials: "include"`).
 * - Adds CSRF header `X-XSRF-TOKEN` for state-changing methods when
 *   `XSRF-TOKEN` is present as a cookie.
 *
 * @param input Request URL or Request object.
 * @param init Optional fetch init override.
 * @returns Browser `fetch` response promise.
 */
export const apiFetch = async (input: RequestInfo | URL, init: RequestInit = {}) => {
  const method = (init.method ?? "GET").toUpperCase();
  const csrfProtectedMethod = CSRF_METHODS.has(method);
  const baseHeaders = new Headers(init.headers ?? {});
  const requestBase = {
    ...init,
    method,
    credentials: init.credentials ?? "include"
  } as RequestInit;

  if (csrfProtectedMethod) {
    const existingCsrfToken = readCookie(CSRF_COOKIE_NAME);
    if (existingCsrfToken == null || existingCsrfToken === "") {
      await primeCsrfTokenCookie(input, requestBase.credentials ?? "include");
    }
  }

  const firstRequestHeaders = new Headers(baseHeaders);
  const csrfToken = csrfProtectedMethod ? readCookie(CSRF_COOKIE_NAME) : null;
  const hasInitialCsrfToken = csrfToken != null && csrfToken !== "";
  if (csrfProtectedMethod && hasInitialCsrfToken) {
    firstRequestHeaders.set(CSRF_HEADER_NAME, csrfToken);
  }

  const response = await fetch(input, {
    ...requestBase,
    headers: firstRequestHeaders
  });

  if (!csrfProtectedMethod || hasInitialCsrfToken) {
    return response;
  }

  if (!await isCsrfInvalidResponse(response)) {
    return response;
  }

  const retryToken = readCookie(CSRF_COOKIE_NAME);
  if (retryToken == null || retryToken === "") {
    return response;
  }

  const retryHeaders = new Headers(baseHeaders);
  retryHeaders.set(CSRF_HEADER_NAME, retryToken);
  return fetch(input, {
    ...requestBase,
    headers: retryHeaders
  });
};

const isCsrfInvalidResponse = async (response: Response): Promise<boolean> => {
  if (response.status !== 403) {
    return false;
  }

  const contentType = response.headers.get("Content-Type") ?? response.headers.get("content-type");
  if (contentType == null || !contentType.toLowerCase().includes("application/json")) {
    return false;
  }

  try {
    const body = await response.clone().json() as {code?: unknown};
    return body.code === CSRF_INVALID_ERROR_CODE;
  } catch {
    return false;
  }
};

const primeCsrfTokenCookie = async (
  input: RequestInfo | URL,
  credentials: RequestCredentials
): Promise<void> => {
  if (csrfPrimeInFlight != null) {
    await csrfPrimeInFlight;
    return;
  }

  const origin = resolveRequestOrigin(input);
  if (origin == null || origin === "") {
    return;
  }

  csrfPrimeInFlight = (async () => {
    try {
      await fetch(origin + "/api/core/profile", {
        method: "GET",
        credentials
      });
    } catch {
      // Ignore priming failures; caller will continue and use retry fallback if needed.
    }
  })();

  try {
    await csrfPrimeInFlight;
  } finally {
    csrfPrimeInFlight = null;
  }
};

const resolveRequestOrigin = (input: RequestInfo | URL): string => {
  if (input instanceof URL) {
    return input.origin;
  }

  if (typeof input === "string") {
    return resolveUrlOrigin(input);
  }

  if (input instanceof Request) {
    return resolveUrlOrigin(input.url);
  }

  return "";
};

const resolveUrlOrigin = (url: string): string => {
  const fallbackOrigin =
    typeof window !== "undefined" && window.location != null
      ? window.location.origin
      : "http://localhost";

  try {
    return new URL(url, fallbackOrigin).origin;
  } catch {
    return fallbackOrigin;
  }
};
