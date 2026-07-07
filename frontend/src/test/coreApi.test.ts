import {describe, expect, it} from "vitest";
import {parseLocationGraph} from "../util/common/coreApi";

describe("parseLocationGraph", () => {
  it("ignores unknown graph response fields", () => {
    const graph = parseLocationGraph({
      id: 7,
      name: "Water Quality Conformance",
      description: "Rolling compliance trend",
      data: [{type: "scatter", x: ["2026-03-01"], y: [4]}],
      ignoredField: [{type: "scatter", x: ["2026-03-01"], y: [99]}],
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(graph.data).toEqual([{type: "scatter", x: ["2026-03-01"], y: [4]}]);
    expect(graph.description).toBe("Rolling compliance trend");
    expect("ignoredField" in graph).toBe(false);
  });

  it("defaults missing graph descriptions to null", () => {
    const graph = parseLocationGraph({
      id: 8,
      name: "No Context",
      data: [{type: "scatter", x: ["2026-03-01"], y: [4]}],
      createdAt: "2026-03-01T00:00:00Z",
      updatedAt: "2026-03-02T00:00:00Z"
    });

    expect(graph.description).toBeNull();
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
