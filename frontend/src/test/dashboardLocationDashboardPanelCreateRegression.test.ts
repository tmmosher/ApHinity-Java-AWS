import {describe, expect, it} from "vitest";
import type {LocationGraph} from "../types/Types";
import {
  buildGraphBaselineIndex,
  pruneDeletedLocationGraphState,
  reconcileLocationGraphs
} from "../util/graph/graphEditor";
import {advanceGraphLoadAnimationToken} from "../pages/authenticated/panels/location/LocationDashboardPanel";

const SCATTER_X_VALUES = [
  "2025-01-01",
  "2025-02-01",
  "2025-03-01",
  "2025-04-01",
  "2025-05-01",
  "2025-06-01"
];

const SCATTER_LAYOUT = {
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

const cloneJson = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

const buildScatterTrace = (name: string, color: string, yValues: number[]) => ({
  type: "scatter",
  name,
  x: [...SCATTER_X_VALUES],
  y: [...yValues],
  line: {color, width: 2},
  mode: "lines+markers",
  marker: {size: 6}
});

const buildScatterGraph = (id: number, name: string, yOffset: number): LocationGraph => ({
  id,
  name,
  data: [
    buildScatterTrace("HPC", "#1f77b4", [14 + yOffset, 13 + yOffset, 12 + yOffset, 11 + yOffset, 13 + yOffset, 12 + yOffset]),
    buildScatterTrace("Endotoxin", "#2ca02c", [6 + yOffset, 5 + yOffset, 7 + yOffset, 6 + yOffset, 5 + yOffset, 6 + yOffset]),
    buildScatterTrace("Legionella", "#d62728", [4 + yOffset, 6 + yOffset, 5 + yOffset, 4 + yOffset, 5 + yOffset, 4 + yOffset]),
    buildScatterTrace("Key Minerals", "#ff7f0e", [10 + yOffset, 9 + yOffset, 8 + yOffset, 9 + yOffset, 10 + yOffset, 9 + yOffset]),
    buildScatterTrace("Alkalinity", "#9467bd", [7 + yOffset, 8 + yOffset, 7 + yOffset, 6 + yOffset, 7 + yOffset, 8 + yOffset])
  ],
  layout: cloneJson(SCATTER_LAYOUT),
  config: {displayModeBar: false, responsive: true},
  style: {height: 320},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

const buildBlankScatterTrace = (name: string, color: string) => ({
  type: "scatter",
  name,
  x: [],
  y: [],
  line: {color, width: 2},
  mode: "lines+markers",
  marker: {size: 6}
});

const buildBlankScatterGraph = (id: number, name: string): LocationGraph => ({
  id,
  name,
  data: [
    buildBlankScatterTrace("HPC", "#1f77b4"),
    buildBlankScatterTrace("Endotoxin", "#2ca02c"),
    buildBlankScatterTrace("Legionella", "#d62728"),
    buildBlankScatterTrace("Key Minerals", "#ff7f0e"),
    buildBlankScatterTrace("Alkalinity", "#9467bd")
  ],
  layout: cloneJson(SCATTER_LAYOUT),
  config: {displayModeBar: false, responsive: true},
  style: {height: 320},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

const buildBarGraph = (id: number, name: string): LocationGraph => ({
  id,
  name,
  data: [{type: "bar", name: "Trace 1", x: ["Point 1"], y: [5]}],
  layout: {showlegend: false},
  config: {displayModeBar: false, responsive: true},
  style: {height: 320},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

const buildPieGraph = (id: number, name: string): LocationGraph => ({
  id,
  name,
  data: [
    {
      type: "pie",
      name: "Distribution",
      labels: ["Open", "Closed"],
      values: [68, 32],
      marker: {
        color: "#2563eb",
        colors: ["#2563eb", "#16a34a"]
      },
      hole: 0.72,
      sort: false,
      textinfo: "none",
      direction: "clockwise",
      hovertemplate: "%{label}: %{value}<extra></extra>"
    }
  ],
  layout: cloneJson({
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
  }),
  config: {displayModeBar: false, responsive: true},
  style: {height: 320},
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

// The Vitest setup uses Solid's server runtime, so this regression is asserted
// as a pure data contract instead of mounting the full dashboard tree.
describe("LocationDashboardPanel create flow regressions", () => {
  it("keeps existing graph payloads intact after creating a new graph and refreshing", () => {
    const existingScatterGraph = buildScatterGraph(11, "Existing Scatter", 0);
    const existingBarGraph = buildBarGraph(12, "Existing Bar");
    const createdGraph = buildBlankScatterGraph(13, "New Plot Graph");

    const refreshedGraphs = reconcileLocationGraphs(
      [existingScatterGraph, existingBarGraph],
      [
        cloneJson(existingScatterGraph),
        cloneJson(existingBarGraph),
        cloneJson(createdGraph)
      ]
    );

    const refreshedScatter = refreshedGraphs.find((graph) => graph.name === "Existing Scatter");
    const refreshedBar = refreshedGraphs.find((graph) => graph.name === "Existing Bar");
    const refreshedCreated = refreshedGraphs.find((graph) => graph.name === "New Plot Graph");

    expect(refreshedScatter).toBe(existingScatterGraph);
    expect(refreshedBar).toBe(existingBarGraph);
    expect(refreshedCreated).not.toBeUndefined();

    expect(refreshedScatter?.data).toHaveLength(5);
    expect(refreshedScatter?.layout).toEqual(SCATTER_LAYOUT);
    expect(refreshedScatter?.data[0]).toMatchObject({
      type: "scatter",
      mode: "lines+markers"
    });
    expect((refreshedScatter?.data[0].y as unknown[])).toEqual([14, 13, 12, 11, 13, 12]);

    expect(refreshedBar?.data).toHaveLength(1);
    expect((refreshedBar?.data[0].y as unknown[])).toEqual([5]);

    expect(refreshedCreated).toEqual(createdGraph);

    expect(advanceGraphLoadAnimationToken(0, true, false, true)).toBe(1);
    expect(advanceGraphLoadAnimationToken(1, true, false, true)).toBe(1);
  });

  it("drops deleted pie graph state before the next graph create flow reuses dashboard caches", () => {
    const deletedPieGraph = buildPieGraph(21, "Deleted Pie");
    const remainingBarGraph = buildBarGraph(22, "Remaining Bar");
    const baselineIndex = buildGraphBaselineIndex([deletedPieGraph, remainingBarGraph]);

    const cleanupResult = pruneDeletedLocationGraphState(
      [deletedPieGraph, remainingBarGraph],
      [[deletedPieGraph, remainingBarGraph]],
      baselineIndex,
      deletedPieGraph.id
    );

    expect(cleanupResult.nextGraphs).toEqual([remainingBarGraph]);
    expect(cleanupResult.nextUndoStack).toEqual([]);
    expect(cleanupResult.nextBaselineIndex.has(deletedPieGraph.id)).toBe(false);

    const createdGraph = buildBlankScatterGraph(23, "New Plot Graph");
    const refreshedGraphs = reconcileLocationGraphs(
      cleanupResult.nextGraphs,
      [cloneJson(remainingBarGraph), cloneJson(createdGraph)]
    );

    expect(refreshedGraphs).toHaveLength(2);
    expect(refreshedGraphs[0]).toBe(remainingBarGraph);
    expect(refreshedGraphs[1]).toEqual(createdGraph);
    for (const trace of refreshedGraphs[1].data) {
      expect(trace).toMatchObject({
        type: "scatter",
        mode: "lines+markers",
        line: {width: 2},
        marker: {size: 6}
      });
      expect(trace.x).toEqual([]);
      expect(trace.y).toEqual([]);
      expect(trace).not.toHaveProperty("labels");
      expect(trace).not.toHaveProperty("values");
    }
  });
});
