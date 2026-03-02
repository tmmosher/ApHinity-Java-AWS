import {describe, expect, it} from "vitest";
import {
  addPieRow,
  coerceInputValue,
  getTraceYAxisRange,
  removePieRow,
  setTraceColor,
  updateTraceYAxisRange,
  updatePieValue
} from "../util/graphTraceEditor";

describe("graphTraceEditor", () => {
  it("keeps intermediate numeric input instead of coercing to zero", () => {
    expect(coerceInputValue("-", 3, true)).toBe("-");
    expect(coerceInputValue(".", 3, true)).toBe(".");
    expect(coerceInputValue("1.", 3, true)).toBe("1.");
  });

  it("syncs pie marker colors when rows are added and removed", () => {
    const baseTrace: Record<string, unknown> = {
      type: "pie",
      labels: ["Open"],
      values: [2],
      marker: {
        color: "#2563eb",
        colors: ["#2563eb"]
      }
    };

    const withAddedRow = addPieRow(baseTrace);
    const addedColors = (withAddedRow.marker as {colors: unknown[]}).colors;
    expect(addedColors).toHaveLength(2);

    const withRemovedRow = removePieRow(withAddedRow, 0);
    const removedColors = (withRemovedRow.marker as {colors: unknown[]}).colors;
    expect(removedColors).toHaveLength(1);
  });

  it("applies scatter color to marker and line", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      x: [1, 2],
      y: [3, 4]
    };

    const nextTrace = setTraceColor(trace, "scatter", "#dc2626");
    expect((nextTrace.marker as {color: string}).color).toBe("#dc2626");
    expect((nextTrace.line as {color: string}).color).toBe("#dc2626");
  });

  it("keeps invalid pie value text for in-progress editing", () => {
    const trace: Record<string, unknown> = {
      type: "pie",
      labels: ["Open"],
      values: [3]
    };

    const nextTrace = updatePieValue(trace, 0, "-");
    expect((nextTrace.values as unknown[])[0]).toBe("-");
  });

  it("updates and reads y-axis range for default axis traces", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      y: [1, 2, 3]
    };

    const withMin = updateTraceYAxisRange(null, trace, 0, "10");
    expect(withMin).toEqual({
      yaxis: {
        range: [10, null]
      }
    });

    const withMinAndMax = updateTraceYAxisRange(withMin, trace, 1, "20");
    expect(getTraceYAxisRange(withMinAndMax, trace)).toEqual([10, 20]);
  });

  it("targets the selected trace y-axis key and preserves axis settings", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      yaxis: "y2",
      y: [4, 5]
    };

    const layout = updateTraceYAxisRange(
      {
        yaxis2: {
          title: {text: "Secondary Axis"}
        }
      },
      trace,
      1,
      "42"
    );

    expect(layout).toEqual({
      yaxis2: {
        title: {text: "Secondary Axis"},
        range: [null, 42]
      }
    });
  });

  it("removes empty y-axis ranges while keeping unrelated layout entries", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      y: [1, 2]
    };

    const withRange = updateTraceYAxisRange(
      {
        title: {text: "Demo"}
      },
      trace,
      0,
      "5"
    );
    const clearedMin = updateTraceYAxisRange(withRange, trace, 0, "");
    const clearedBoth = updateTraceYAxisRange(clearedMin, trace, 1, "");

    expect(clearedBoth).toEqual({
      title: {text: "Demo"}
    });
  });
});
