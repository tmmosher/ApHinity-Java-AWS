import {afterAll, beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/apiFetch";

describe("apiFetch", () => {
  const originalFetch = globalThis.fetch;
  const originalDocument = (globalThis as {document?: unknown}).document;

  const installDocumentCookie = (cookie: string) => {
    Object.defineProperty(globalThis, "document", {
      value: {cookie},
      configurable: true,
      writable: true
    });
  };

  beforeEach(() => {
    installDocumentCookie("");
    globalThis.fetch = vi.fn() as unknown as typeof fetch;
  });

  it("includes credentials and omits CSRF header for safe methods", async () => {
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock.mockResolvedValue(new Response(null, {status: 204}));

    await apiFetch("https://example.test/api/core/locations", {
      method: "GET"
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    const requestHeaders = requestInit.headers as Headers;
    expect(requestInit.credentials).toBe("include");
    expect(requestHeaders.get("X-XSRF-TOKEN")).toBeNull();
  });

  it("attaches the CSRF header when the token cookie is present", async () => {
    installDocumentCookie("XSRF-TOKEN=token-123");
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock.mockResolvedValue(new Response(null, {status: 204}));

    await apiFetch("https://example.test/api/core/locations/42/graphs", {
      method: "PUT",
      body: JSON.stringify({graphs: []})
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    const requestHeaders = requestInit.headers as Headers;
    expect(requestHeaders.get("X-XSRF-TOKEN")).toBe("token-123");
  });

  it("primes a CSRF cookie before mutating calls when none exists", async () => {
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockImplementationOnce(async () => {
        ((globalThis as {document: {cookie: string}}).document).cookie = "XSRF-TOKEN=token-prime";
        return new Response(null, {status: 200});
      })
      .mockResolvedValueOnce(new Response(null, {status: 204}));

    const response = await apiFetch("https://example.test/api/core/locations/42/graphs", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({graphs: []})
    });

    expect(response.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("https://example.test/api/core/profile");

    const updateRequestHeaders = fetchMock.mock.calls[1][1]?.headers as Headers;
    expect(updateRequestHeaders.get("X-XSRF-TOKEN")).toBe("token-prime");
  });

  it("retries mutating requests once when the server issues the CSRF cookie on the first 403", async () => {
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(null, {status: 200}))
      .mockImplementationOnce(async () => {
        ((globalThis as {document: {cookie: string}}).document).cookie = "XSRF-TOKEN=token-retry";
        return new Response(
          JSON.stringify({code: "csrf_invalid"}),
          {
            status: 403,
            headers: {
              "Content-Type": "application/json"
            }
          }
        );
      })
      .mockResolvedValueOnce(new Response(null, {status: 204}));

    const response = await apiFetch("https://example.test/api/core/locations/42/graphs", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({graphs: []})
    });

    expect(response.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledTimes(3);

    expect(fetchMock.mock.calls[0][0]).toBe("https://example.test/api/core/profile");
    const firstMutationHeaders = fetchMock.mock.calls[1][1]?.headers as Headers;
    const retryMutationHeaders = fetchMock.mock.calls[2][1]?.headers as Headers;
    expect(firstMutationHeaders.get("X-XSRF-TOKEN")).toBeNull();
    expect(retryMutationHeaders.get("X-XSRF-TOKEN")).toBe("token-retry");
  });

  it("does not retry non-CSRF 403 responses", async () => {
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(null, {status: 200}))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({code: "insufficient_permissions"}),
          {
            status: 403,
            headers: {
              "Content-Type": "application/json"
            }
          }
        )
      );

    const response = await apiFetch("https://example.test/api/core/locations/42/memberships/5", {
      method: "DELETE"
    });

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("https://example.test/api/core/profile");
  });

  afterAll(() => {
    globalThis.fetch = originalFetch;
    if (originalDocument === undefined) {
      delete (globalThis as {document?: unknown}).document;
    } else {
      Object.defineProperty(globalThis, "document", {
        value: originalDocument,
        configurable: true,
        writable: true
      });
    }
  });
});
