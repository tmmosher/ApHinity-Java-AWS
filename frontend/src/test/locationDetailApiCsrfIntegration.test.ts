import {afterAll, beforeEach, describe, expect, it, vi} from "vitest";
import {saveLocationGraphsById} from "../util/graph/locationDetailApi";

describe("locationDetailApi + apiFetch CSRF integration", () => {
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

  it("retries graph save after a stale CSRF rejection and succeeds with a refreshed token", async () => {
    installDocumentCookie("XSRF-TOKEN=token-stale");
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(
        JSON.stringify({code: "csrf_invalid"}),
        {
          status: 403,
          headers: {
            "Content-Type": "application/json"
          }
        }
      ))
      .mockImplementationOnce(async () => {
        ((globalThis as {document: {cookie: string}}).document).cookie = "XSRF-TOKEN=token-fresh";
        return new Response(JSON.stringify({}), {
          status: 200,
          headers: {
            "Content-Type": "application/json"
          }
        });
      })
      .mockResolvedValueOnce(new Response(null, {status: 204}));

    await expect(
      saveLocationGraphsById("https://example.test", "42", [
        {
          graphId: 7,
          data: [{type: "bar", y: [1, 2, 3]}]
        }
      ])
    ).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe("https://example.test/api/core/profile");
    const firstMutationHeaders = fetchMock.mock.calls[0][1]?.headers as Headers;
    const retryMutationHeaders = fetchMock.mock.calls[2][1]?.headers as Headers;
    expect(firstMutationHeaders.get("X-XSRF-TOKEN")).toBe("token-stale");
    expect(retryMutationHeaders.get("X-XSRF-TOKEN")).toBe("token-fresh");
  });

  it("surfaces a CSRF error when stale-token retry still fails", async () => {
    installDocumentCookie("XSRF-TOKEN=token-stale");
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(
        JSON.stringify({code: "csrf_invalid"}),
        {
          status: 403,
          headers: {
            "Content-Type": "application/json"
          }
        }
      ))
      .mockResolvedValueOnce(new Response(JSON.stringify({}), {
        status: 200,
        headers: {
          "Content-Type": "application/json"
        }
      }))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({code: "csrf_invalid"}),
        {
          status: 403,
          headers: {
            "Content-Type": "application/json"
          }
        }
      ));

    await expect(
      saveLocationGraphsById("https://example.test", "42", [
        {
          graphId: 7,
          data: [{type: "bar", y: [1, 2, 3]}]
        }
      ])
    ).rejects.toThrowError("CSRF invalid");
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
