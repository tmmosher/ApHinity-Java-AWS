import {afterAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
  saveLocationGraphsById,
  uploadLocationDashboardSpreadsheetById
} from "../util/graph/locationDetailApi";

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
    const firstMutationBody = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(firstMutationHeaders.get("X-XSRF-TOKEN")).toBe("token-stale");
    expect(retryMutationHeaders.get("X-XSRF-TOKEN")).toBe("token-fresh");
    expect(firstMutationBody.graphs[0]).toEqual({
      graphId: 7,
      data: [{type: "bar", y: [1, 2, 3]}]
    });
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

  it("uploads a dashboard spreadsheet as multipart form data", async () => {
    installDocumentCookie("XSRF-TOKEN=token-fresh");
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify([
      {
        id: 18,
        name: "Water Quality Compliance",
        data: [{type: "scatter", name: "HPC", x: ["2025-08-01"], y: [50]}],
        layout: {meta: {aphinityImport: {graphId: "graph-1"}}},
        config: {},
        style: {},
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z"
      }
    ]), {
      status: 200,
      headers: {"Content-Type": "application/json"}
    }));

    const file = new File(["workbook"], "dashboard.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    });

    await expect(uploadLocationDashboardSpreadsheetById("https://example.test", "42", file)).resolves.toMatchObject([
      {
        id: 18,
        name: "Water Quality Compliance"
      }
    ]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("https://example.test/api/core/locations/42/dashboard/spreadsheet-upload");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.headers as Headers).get("X-XSRF-TOKEN")).toBe("token-fresh");
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
