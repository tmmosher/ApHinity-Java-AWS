import {describe, expect, it} from "vitest";
import type {LocationGraph} from "../types/Types";
import {
  buildCreateLocationGraphRequest,
  buildPostCreateGraphUpdate,
  DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE,
  getGraphYAxisTitle,
  normalizeGraphCreateYAxisTitle,
  updateGraphYAxisTitle
} from "../util/graph/graphCreate";

const buildScatterGraph = (yAxisTitle = DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE): LocationGraph => ({
  id: 44,
  name: "New Plot Graph",
  data: [{
    type: "scatter",
    name: "Trace 1",
    x: [],
    y: [],
    line: {color: "#2563eb", width: 2},
    mode: "lines+markers",
    marker: {size: 6}
  }],
  layout: {
    title: {x: 0.02, text: "", xanchor: "left"},
    xaxis: {type: "date", tickformat: "%b %Y"},
    yaxis: {range: [0, 100], title: yAxisTitle, ticksuffix: "%"},
    legend: {x: 0, y: -0.3, orientation: "h"},
    margin: {b: 60, l: 50, r: 20, t: 50}
  },
  config: {displayModeBar: false, responsive: false},
  style: {height: 320},
  createdAt: "2026-01-05T00:00:00Z",
  updatedAt: "2026-01-05T00:00:00Z"
});

describe("graphCreate utilities", () => {
  it("keeps the create endpoint contract free of custom axis fields", () => {
    expect(buildCreateLocationGraphRequest({
      graphType: "scatter",
      sectionId: 3,
      createNewSection: false,
      yAxisTitle: "My custom axis"
    })).toEqual({
      graphType: "scatter",
      sectionId: 3,
      createNewSection: false
    });
  });

  it("defaults blank y-axis titles to compliance", () => {
    expect(normalizeGraphCreateYAxisTitle("   ")).toBe(DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE);
    expect(normalizeGraphCreateYAxisTitle(undefined)).toBe(DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE);
  });

  it("builds a follow-up update only when the requested scatter y-axis title changes", () => {
    const graph = buildScatterGraph();

    expect(buildPostCreateGraphUpdate(graph, {
      graphType: "scatter",
      createNewSection: false,
      sectionId: 2,
      yAxisTitle: DEFAULT_SCATTER_GRAPH_Y_AXIS_TITLE
    })).toBeNull();

    expect(buildPostCreateGraphUpdate(graph, {
      graphType: "bar",
      createNewSection: false,
      sectionId: 2,
      yAxisTitle: "Monthly totals"
    })).toBeNull();
  });

  it("builds a follow-up update that preserves the existing graph payload", () => {
    const graph = buildScatterGraph("% Non-Compliance");

    expect(buildPostCreateGraphUpdate(graph, {
      graphType: "scatter",
      createNewSection: false,
      sectionId: 2,
      yAxisTitle: "Monthly compliance"
    })).toEqual({
      graphId: 44,
      data: graph.data,
      layout: {
        ...graph.layout,
        yaxis: {
          range: [0, 100],
          title: "Monthly compliance",
          ticksuffix: "%"
        }
      },
      config: graph.config,
      style: graph.style,
      expectedUpdatedAt: "2026-01-05T00:00:00Z"
    });
  });

  it("reads and updates object-based axis titles without dropping existing metadata", () => {
    const nextLayout = updateGraphYAxisTitle({
      yaxis: {
        range: [0, 100],
        title: {text: "% Non-Compliance", font: {size: 14}}
      }
    }, "Target compliance");

    expect(getGraphYAxisTitle(nextLayout)).toBe("Target compliance");
    expect(nextLayout).toEqual({
      yaxis: {
        range: [0, 100],
        title: {text: "Target compliance", font: {size: 14}}
      }
    });
  });
});
