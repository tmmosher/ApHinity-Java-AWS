const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

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
export const apiFetch = (input: RequestInfo | URL, init: RequestInit = {}) => {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers ?? {});

  if (CSRF_METHODS.has(method)) {
    const csrfToken = readCookie(CSRF_COOKIE_NAME);
    if (csrfToken != null && csrfToken !== "") {
      headers.set(CSRF_HEADER_NAME, csrfToken);
    }
  }

  return fetch(input, {
    ...init,
    method,
    credentials: init.credentials ?? "include",
    headers
  });
};