import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
  materializeLocationGraphForTimeRange,
  resolveLocationGraphDataForTimeRange
} from "../util/location/dashboardTimeRange";
import type {LocationGraph} from "../types/Types";

const buildGraph = (): LocationGraph => ({
  id: 11,
  name: "Water Quality Compliance",
  data: [
    {
      type: "scatter",
      name: "HPC",
      x: ["2026-03-01", "2025-01-01", "2025-12-01", "2026-01-01"],
      y: [6, 4, 5, 5.5],
      customdata: [{sampleCount: 6}, {sampleCount: 4}, {sampleCount: 5}, {sampleCount: 55}]
    },
    {
      type: "bar",
      name: "Summary",
      x: [7],
      y: ["All Data"]
    }
  ],
  layout: null,
  config: null,
  style: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z"
});

describe("dashboardTimeRange helpers", () => {
  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date"]});
    vi.setSystemTime(new Date("2026-03-20T08:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps all-time views on the canonical all-time graph data", () => {
    const graph = buildGraph();

    expect(resolveLocationGraphDataForTimeRange(graph, "allTime")).toBe(graph.data);
    expect(materializeLocationGraphForTimeRange(graph, "allTime")).toBe(graph);
  });

  it("derives three-month views from all-time data with one month of hidden context", () => {
    const graph = buildGraph();
    const displayGraph = materializeLocationGraphForTimeRange(graph, "threeMonths");

    expect(displayGraph).not.toBe(graph);
    expect(displayGraph.data).toEqual([
      {
        type: "scatter",
        name: "HPC",
        x: ["2025-12-01", "2026-01-01", "2026-03-01"],
        y: [5, 5.5, 6],
        customdata: [{sampleCount: 5}, {sampleCount: 55}, {sampleCount: 6}]
      },
      {
        type: "bar",
        name: "Summary",
        x: [7],
        y: ["All Data"]
      }
    ]);
    expect(displayGraph.layout).toMatchObject({
      xaxis: {
        range: ["2025-11-24", "2026-03-27"],
        autorange: false
      }
    });
    expect(graph.data[0].x).toEqual(["2026-03-01", "2025-01-01", "2025-12-01", "2026-01-01"]);
  });

  it("derives twelve-month views from all-time data with a one-week display margin", () => {
    const graph = buildGraph();
    const displayGraph = materializeLocationGraphForTimeRange(graph, "twelveMonths");

    expect(displayGraph.data[0]).toMatchObject({
      x: ["2025-12-01", "2026-01-01", "2026-03-01"],
      y: [5, 5.5, 6]
    });
    expect(displayGraph.layout).toMatchObject({
      xaxis: {
        range: ["2025-03-13", "2026-03-27"],
        autorange: false
      }
    });
  });

  it("filters every time-series trace independently and leaves categorical traces intact", () => {
    const graph = buildGraph();
    graph.data = [
      ...graph.data,
      {type: "scattergl", name: "Endotoxin", x: ["2025-11-15", "2026-02-01"], y: [1, 2]},
      {type: "scatter", name: "Categorical Scatter", x: ["A", "B"], y: [1, 2]}
    ];

    const displayData = resolveLocationGraphDataForTimeRange(graph, "threeMonths");

    expect(displayData[2]).toEqual({
      type: "scattergl",
      name: "Endotoxin",
      x: ["2025-11-15", "2026-02-01"],
      y: [1, 2]
    });
    expect(displayData[3]).toBe(graph.data[3]);
  });
});
