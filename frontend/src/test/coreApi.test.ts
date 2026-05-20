import {describe, expect, it} from "vitest";
import {parseLocationGraph} from "../util/common/coreApi";

describe("parseLocationGraph", () => {
  it("parses known time range payloads and ignores unknown keys", () => {
    const graph = parseLocationGraph({
      id: 7,
      name: "Water Quality Conformance",
      data: [{type: "scatter", x: ["2026-03-01"], y: [4]}],
      timeRangeData: {
        threeMonths: [{type: "scatter", x: ["2026-01-01", "2026-03-01"], y: [2, 4]}],
        twelveMonths: [{type: "scatter", x: ["2025-03-01", "2026-03-01"], y: [1, 4]}],
        allTime: [{type: "scatter", x: ["2025-01-01", "2026-03-01"], y: [3, 4]}],
        unsupported: [{type: "scatter", x: ["2026-03-01"], y: [99]}]
      },
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(graph.timeRangeData?.threeMonths).toEqual([{type: "scatter", x: ["2026-01-01", "2026-03-01"], y: [2, 4]}]);
    expect(graph.timeRangeData?.twelveMonths).toEqual([{type: "scatter", x: ["2025-03-01", "2026-03-01"], y: [1, 4]}]);
    expect(graph.timeRangeData?.allTime).toEqual([{type: "scatter", x: ["2025-01-01", "2026-03-01"], y: [3, 4]}]);
    expect("unsupported" in (graph.timeRangeData ?? {})).toBe(false);
  });

  it("maps legacy oneMonth/threeMonths payloads to the new threeMonths/twelveMonths keys", () => {
    const graph = parseLocationGraph({
      id: 8,
      name: "Legacy Time Ranges",
      data: [{type: "scatter", x: ["2026-03-01"], y: [4]}],
      timeRangeData: {
        oneMonth: [{type: "scatter", x: ["2026-03-01"], y: [1]}],
        threeMonths: [{type: "scatter", x: ["2026-01-01", "2026-03-01"], y: [2, 4]}],
        allTime: [{type: "scatter", x: ["2025-01-01", "2026-03-01"], y: [3, 4]}]
      },
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(graph.timeRangeData?.threeMonths).toEqual([{type: "scatter", x: ["2026-03-01"], y: [1]}]);
    expect(graph.timeRangeData?.twelveMonths).toEqual([{type: "scatter", x: ["2026-01-01", "2026-03-01"], y: [2, 4]}]);
  });

  it("keeps supporting the legacy nested graph payload shape", () => {
    const graph = parseLocationGraph({
      id: 9,
      name: "Legacy Graph",
      data: {
        data: [{type: "bar", x: [3], y: ["Legacy"]}],
        layout: {title: {text: "Legacy"}},
        config: {responsive: true},
        style: {height: 320}
      },
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(graph.data).toEqual([{type: "bar", x: [3], y: ["Legacy"]}]);
    expect(graph.layout).toEqual({title: {text: "Legacy"}});
    expect(graph.config).toEqual({responsive: true});
    expect(graph.style).toEqual({height: 320});
  });
});
