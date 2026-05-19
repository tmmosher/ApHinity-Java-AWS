import {describe, expect, it} from "vitest";
import {
  materializeLocationGraphForTimeRange,
  resolveLocationGraphDataForTimeRange
} from "../util/location/dashboardTimeRange";
import type {LocationGraph} from "../types/Types";

const buildGraph = (): LocationGraph => ({
  id: 11,
  name: "Water Quality Compliance",
  data: [{type: "scatter", name: "All", x: ["2026-01-01"], y: [5]}],
  timeRangeData: {
    oneMonth: [{type: "scatter", name: "1M", x: ["2026-03-01"], y: [1]}],
    threeMonths: [{type: "scatter", name: "3M", x: ["2026-01-01", "2026-02-01"], y: [2, 3]}],
    allTime: [{type: "scatter", name: "All", x: ["2025-01-01", "2026-03-01"], y: [4, 5]}]
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

    expect(resolveLocationGraphDataForTimeRange(graph, "oneMonth")).toEqual(graph.timeRangeData?.oneMonth);
    expect(resolveLocationGraphDataForTimeRange(graph, "threeMonths")).toEqual(graph.timeRangeData?.threeMonths);
    expect(resolveLocationGraphDataForTimeRange(graph, "allTime")).toEqual(graph.timeRangeData?.allTime);
  });

  it("falls back to the canonical graph data when a range payload is missing", () => {
    const graph = buildGraph();
    delete graph.timeRangeData?.oneMonth;

    expect(resolveLocationGraphDataForTimeRange(graph, "oneMonth")).toEqual(graph.data);
  });

  it("materializes a display graph without mutating the source graph", () => {
    const graph = buildGraph();
    const displayGraph = materializeLocationGraphForTimeRange(graph, "threeMonths");

    expect(displayGraph).not.toBe(graph);
    expect(displayGraph.data).toEqual(graph.timeRangeData?.threeMonths);
    expect(graph.data).toEqual([{type: "scatter", name: "All", x: ["2026-01-01"], y: [5]}]);
  });

  it("reuses the original graph when the resolved payload already matches the canonical data", () => {
    const graph = buildGraph();

    expect(materializeLocationGraphForTimeRange(graph, "allTime")).toBe(graph);

    delete graph.timeRangeData?.oneMonth;
    expect(materializeLocationGraphForTimeRange(graph, "oneMonth")).toBe(graph);
  });
});
