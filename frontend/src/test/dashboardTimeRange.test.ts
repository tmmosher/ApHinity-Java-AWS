import {describe, expect, it} from "vitest";
import {
  materializeLocationGraphForTimeRange,
  resolveLocationGraphDataForTimeRange
} from "../util/location/dashboardTimeRange";
import type {LocationGraph} from "../types/Types";

const buildGraph = (): LocationGraph => ({
  id: 11,
  name: "Water Quality Compliance",
  data: [{type: "scatter", name: "All", x: ["2025-01-01", "2026-01-01", "2026-02-01", "2026-03-01"], y: [4, 5, 5.5, 6]}],
  timeRangeData: {
    threeMonths: [{type: "scatter", name: "3M", x: ["2026-01-01", "2026-02-01"], y: [2, 3]}],
    twelveMonths: [{type: "scatter", name: "12M", x: ["2025-03-01", "2026-01-01", "2026-02-01"], y: [1.5, 2, 3]}],
    allTime: [{type: "scatter", name: "All", x: ["2025-01-01", "2026-01-01", "2026-02-01", "2026-03-01"], y: [4, 5, 5.5, 6]}]
  },
  layout: null,
  config: null,
  style: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

describe("dashboardTimeRange helpers", () => {
  it("returns the requested graph slice when range data is available", () => {
    const graph = buildGraph();

    expect(resolveLocationGraphDataForTimeRange(graph, "threeMonths")).toEqual(graph.timeRangeData?.threeMonths);
    expect(resolveLocationGraphDataForTimeRange(graph, "twelveMonths")).toEqual(graph.timeRangeData?.twelveMonths);
    expect(resolveLocationGraphDataForTimeRange(graph, "allTime")).toEqual(graph.data);
  });

  it("falls back to the canonical graph data when a range payload is missing", () => {
    const graph = buildGraph();
    delete graph.timeRangeData?.threeMonths;

    expect(resolveLocationGraphDataForTimeRange(graph, "threeMonths")).toEqual(graph.data);
  });

  it("materializes a display graph without mutating the source graph", () => {
    const graph = buildGraph();
    const displayGraph = materializeLocationGraphForTimeRange(graph, "threeMonths");

    expect(displayGraph).not.toBe(graph);
    expect(displayGraph.data).toEqual([{
      type: "scatter",
      name: "3M",
      x: ["2025-01-01", "2026-01-01", "2026-02-01"],
      y: [4, 2, 3]
    }]);
    expect(displayGraph.layout).toMatchObject({
      xaxis: {
        range: ["2026-01-01", "2026-02-01"],
        autorange: false
      }
    });
    expect(graph.data).toEqual([{type: "scatter", name: "All", x: ["2025-01-01", "2026-01-01", "2026-02-01", "2026-03-01"], y: [4, 5, 5.5, 6]}]);
  });

  it("reuses the original graph when the resolved payload already matches the canonical data", () => {
    const graph = buildGraph();
    graph.timeRangeData = {
      ...graph.timeRangeData,
      allTime: graph.data
    };

    expect(materializeLocationGraphForTimeRange(graph, "allTime")).toBe(graph);

    delete graph.timeRangeData?.threeMonths;
    expect(materializeLocationGraphForTimeRange(graph, "threeMonths")).toBe(graph);
  });

  it("prefers the editable canonical payload for all-time views even when timeRangeData.allTime is stale", () => {
    const graph = buildGraph();
    graph.timeRangeData = {
      ...graph.timeRangeData,
      allTime: [{type: "scatter", name: "All", x: ["2025-01-01"], y: [99]}]
    };

    expect(resolveLocationGraphDataForTimeRange(graph, "allTime")).toEqual(graph.data);
    expect(materializeLocationGraphForTimeRange(graph, "allTime")).toBe(graph);
  });

  it("prepends the prior all-time datapoint for three-month views and keeps it outside the visible range", () => {
    const graph = buildGraph();
    const displayGraph = materializeLocationGraphForTimeRange(graph, "threeMonths");

    expect(displayGraph.data).toEqual([{
      type: "scatter",
      name: "3M",
      x: ["2025-01-01", "2026-01-01", "2026-02-01"],
      y: [4, 2, 3]
    }]);
    expect(displayGraph.layout).toMatchObject({
      xaxis: {
        range: ["2026-01-01", "2026-02-01"],
        autorange: false
      }
    });
  });

  it("uses the current canonical payload as predecessor context for ranged views", () => {
    const graph = buildGraph();
    graph.data = [{type: "scatter", name: "All", x: ["2025-12-01", "2026-02-15", "2026-03-01"], y: [8, 9, 10]}];
    graph.timeRangeData = {
      ...graph.timeRangeData,
      allTime: [{type: "scatter", name: "All", x: ["2025-01-01", "2026-01-01", "2026-02-01", "2026-03-01"], y: [4, 5, 5.5, 6]}]
    };

    const displayGraph = materializeLocationGraphForTimeRange(graph, "threeMonths");

    expect(displayGraph.data).toEqual([{
      type: "scatter",
      name: "3M",
      x: ["2025-12-01", "2026-01-01", "2026-02-01"],
      y: [8, 2, 3]
    }]);
  });
});
