import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../util/common/apiFetch";
import {
  createLocationGraphById,
  deleteLocationGraphById,
  fetchLocationById,
  fetchLocationGraphsById,
  renameLocationGraphById,
  parseRouteLocationId,
  saveLocationGraphsById
} from "../util/graph/locationDetailApi";

vi.mock("../util/common/apiFetch", () => ({
  apiFetch: vi.fn()
}));

const createMockResponse = (ok: boolean, payload: unknown): Response =>
  ({
    ok,
    json: vi.fn().mockResolvedValue(payload)
  }) as unknown as Response;

const SCATTER_X_VALUES = [
  "2025-01-01",
  "2025-02-01",
  "2025-03-01",
  "2025-04-01",
  "2025-05-01",
  "2025-06-01"
];

const createScatterTrace = (name: string, color: string, yValues: number[]) => ({
  type: "scatter",
  name,
  x: [...SCATTER_X_VALUES],
  y: yValues,
  line: {color, width: 2},
  mode: "lines+markers",
  marker: {size: 6}
});

const SCATTER_GRAPH_LAYOUT = {
  margin: {b: 10, l: 10, r: 10, t: 10},
  showlegend: false,
  annotations: [
    {
      x: 0.5,
      y: 0.5,
      font: {size: 22},
      text: "<b>68%</b>",
      xref: "paper",
      yref: "paper",
      showarrow: false
    }
  ]
};

const buildScatterGraphResponse = () => ({
  id: 44,
  name: "New Plot Graph",
  data: [
    createScatterTrace("HPC", "#1f77b4", [14, 13, 12, 11, 13, 12]),
    createScatterTrace("Endotoxin", "#2ca02c", [6, 5, 7, 6, 5, 6]),
    createScatterTrace("Legionella", "#d62728", [4, 6, 5, 4, 5, 4]),
    createScatterTrace("Key Minerals", "#ff7f0e", [10, 9, 8, 9, 10, 9]),
    createScatterTrace("Alkalinity", "#9467bd", [7, 8, 7, 6, 7, 8])
  ],
  layout: SCATTER_GRAPH_LAYOUT,
  config: {displayModeBar: false, responsive: true},
  style: {height: 320},
  createdAt: "2026-01-05T00:00:00Z",
  updatedAt: "2026-01-05T00:00:00Z"
});

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

  it("saves location graph edits through the update endpoint", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {}));

    await saveLocationGraphsById(host, "55", [
      {
        graphId: 12,
        data: [{type: "pie", values: [1, 2, 3]}],
        layout: {title: {text: "Demo"}},
        config: {displayModeBar: false},
        style: {height: 280}
      }
    ]);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        graphs: [
          {
            graphId: 12,
            data: [{type: "pie", values: [1, 2, 3]}],
            layout: {title: {text: "Demo"}},
            config: {displayModeBar: false},
            style: {height: 280}
          }
        ]
      })
    });
  });

  it("saves minimal graph updates (graphId + data only)", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {}));

    await saveLocationGraphsById(host, "55", [
      {
        graphId: 99,
        data: [{type: "bar", y: [4, 5, 6]}]
      }
    ]);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        graphs: [
          {
            graphId: 99,
            data: [{type: "bar", y: [4, 5, 6]}]
          }
        ]
      })
    });
  });

  it("passes expectedUpdatedAt through the save payload", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {}));

    await saveLocationGraphsById(host, "55", [
      {
        graphId: 99,
        data: [{type: "bar", y: [4, 5, 6]}],
        expectedUpdatedAt: "2026-01-04T00:00:00Z"
      }
    ]);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        graphs: [
          {
            graphId: 99,
            data: [{type: "bar", y: [4, 5, 6]}],
            expectedUpdatedAt: "2026-01-04T00:00:00Z"
          }
        ]
      })
    });
  });

  it("renames a graph through the dedicated name endpoint", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      graphId: 12,
      name: "Updated graph title",
      updatedAt: "2026-01-05T00:00:00Z"
    }));

    const result = await renameLocationGraphById(host, "55", 12, "Updated graph title");

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs/12/name", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Updated graph title"
      })
    });
    expect(result).toEqual({
      graphId: 12,
      name: "Updated graph title",
      updatedAt: "2026-01-05T00:00:00Z"
    });
  });

  it("creates a graph through the dedicated create endpoint", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, buildScatterGraphResponse()));

    const result = await createLocationGraphById(host, "55", {
      sectionId: 3,
      graphType: "scatter"
    });

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        sectionId: 3,
        graphType: "scatter"
      })
    });
    expect(result.name).toBe("New Plot Graph");
    expect(result.data).toHaveLength(5);
    expect(result.data[0]).toMatchObject({
      type: "scatter",
      mode: "lines+markers",
      line: {color: "#1f77b4", width: 2},
      marker: {size: 6}
    });
    expect(result.layout).toEqual(SCATTER_GRAPH_LAYOUT);
    expect(result.config).toEqual({displayModeBar: false, responsive: true});
  });

  it("creates a graph and requests a new section when needed", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {
      id: 45,
      name: "New Bar Graph",
      data: [{type: "bar", name: "Trace 1", x: ["Point 1"], y: [0]}],
      layout: {showlegend: false},
      config: {displayModeBar: false, responsive: true},
      style: {height: 320},
      createdAt: "2026-01-05T00:00:00Z",
      updatedAt: "2026-01-05T00:00:00Z"
    }));

    const result = await createLocationGraphById(host, "55", {
      createNewSection: true,
      graphType: "bar"
    });

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        createNewSection: true,
        graphType: "bar"
      })
    });
    expect(result.name).toBe("New Bar Graph");
    expect(result.data[0].type).toBe("bar");
  });

  it("deletes a graph through the dedicated delete endpoint", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(true, {}));

    await deleteLocationGraphById(host, "55", 12);

    expect(apiFetchMock).toHaveBeenCalledWith(host + "/api/core/locations/55/graphs/12", {
      method: "DELETE"
    });
  });

  it("surfaces permission errors when graph creation is rejected", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "forbidden",
      message: "Insufficient permissions"
    }));

    await expect(
      createLocationGraphById(host, "55", {
        sectionId: 3,
        graphType: "bar"
      })
    ).rejects.toThrowError("Insufficient permissions");
  });

  it("rejects invalid route location ids before save request dispatch", async () => {
    await expect(
      saveLocationGraphsById(host, "0", [
        {
          graphId: 1,
          data: [{type: "bar", y: [1]}]
        }
      ])
    ).rejects.toThrowError("Invalid location id");

    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("throws when graph updates fail to save", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {}));

    await expect(
      saveLocationGraphsById(host, "55", [])
    ).rejects.toThrowError("Unable to save location graphs");
  });

  it("throws a conflict error when the server reports graph update conflict", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "graph_update_conflict",
      message: "Graph update conflict"
    }));

    await expect(
      saveLocationGraphsById(host, "55", [])
    ).rejects.toThrowError("Graph update conflict");
  });

  it("throws a csrf error when the server reports csrf_invalid", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "csrf_invalid",
      message: "Missing or invalid CSRF token"
    }));

    await expect(
      saveLocationGraphsById(host, "55", [])
    ).rejects.toThrowError("CSRF invalid");
  });

  it("throws an authentication error when the server reports authentication_required", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "authentication_required",
      message: "Authentication required"
    }));

    await expect(
      saveLocationGraphsById(host, "55", [])
    ).rejects.toThrowError("Authentication required");
  });

  it("throws a security-token error for generic Spring Security 403 payloads", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      timestamp: "2026-03-06T21:20:05.325Z",
      status: 403,
      error: "Forbidden",
      path: "/api/core/locations/2/graphs"
    }));

    await expect(
      saveLocationGraphsById(host, "55", [])
    ).rejects.toThrowError("Security token rejected");
  });

  it("throws a graph-name error when rename validation fails", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "graph_name_required",
      message: "Graph name is required"
    }));

    await expect(
      renameLocationGraphById(host, "55", 12, "   ")
    ).rejects.toThrowError("Graph name is required");
  });

  it("throws a location-graph error when delete target no longer exists", async () => {
    apiFetchMock.mockResolvedValue(createMockResponse(false, {
      code: "location_graph_not_found",
      message: "Location graph not found"
    }));

    await expect(
      deleteLocationGraphById(host, "55", 12)
    ).rejects.toThrowError("Location graph not found");
  });
});
