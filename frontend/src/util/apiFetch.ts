const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

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
