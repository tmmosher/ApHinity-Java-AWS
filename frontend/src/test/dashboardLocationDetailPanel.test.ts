import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/apiFetch";
import {
  fetchLocationById,
  fetchLocationGraphsById,
  parseRouteLocationId
} from "../pages/authenticated/panels/locationDetailApi";

vi.mock("../util/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown): Response =>
  ({
    ok,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

describe("DashboardLocationDetailPanel data loaders", () => {
  const host = "https://example.test";
  const apiFetchMock = vi.mocked(apiFetch);

  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("parses a valid route location id", () => {
    expect(parseRouteLocationId("42")).toBe(42);
    expect(parseRouteLocationId("007")).toBe(7);
  });

  it("rejects invalid route location ids", () => {
    expect(() => parseRouteLocationId("0")).toThrowError("Invalid location id");
    expect(() => parseRouteLocationId("-9")).toThrowError("Invalid location id");
    expect(() => parseRouteLocationId("abc")).toThrowError("Invalid location id");
  });

  it("requests and parses a location summary", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 55,
      name: "Austin",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
      sectionLayout: {
        sections: [
          {section_id: 2, graph_ids: [11, 12]}
        ]
      }
    }));

    const location = await fetchLocationById(host, "55");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55", {
      method: "GET"
    });
    expect(location.name).toBe("Austin");
    expect(location.sectionLayout.sections[0].graph_ids).toEqual([11, 12]);
  });

  it("requests and parses location graphs", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 11,
        name: "Daily sessions",
        data: [{type: "bar", x: [1], y: [9]}],
        layout: {title: "Sessions"},
        config: {displayModeBar: false},
        style: {height: 320},
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-03T00:00:00Z"
      }
    ]));

    const graphs = await fetchLocationGraphsById(host, "55");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "GET"
    });
    expect(graphs).toHaveLength(1);
    expect(graphs[0].name).toBe("Daily sessions");
    expect(graphs[0].data[0].type).toBe("bar");
    expect(graphs[0].layout).toEqual({title: "Sessions"});
    expect(graphs[0].config).toEqual({displayModeBar: false});
    expect(graphs[0].style).toEqual({height: 320});
  });

  it("normalizes legacy nested graph payloads", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, [
      {
        id: 12,
        name: "Weekly sessions",
        data: {
          data: [{type: "scatter", x: [1], y: [5]}],
          layout: {title: "Legacy layout"},
          config: {displayModeBar: true},
          style: {height: 280}
        },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-04T00:00:00Z"
      }
    ]));

    const graphs = await fetchLocationGraphsById(host, "55");

    expect(graphs).toHaveLength(1);
    expect(graphs[0].data[0].type).toBe("scatter");
    expect(graphs[0].layout).toEqual({title: "Legacy layout"});
    expect(graphs[0].config).toEqual({displayModeBar: true});
    expect(graphs[0].style).toEqual({height: 280});
  });

  it("fails when graph API returns non-success status", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, []));

    await expect(fetchLocationGraphsById(host, "55"))
      .rejects
      .toThrowError("Unable to load location graphs");
  });
});
