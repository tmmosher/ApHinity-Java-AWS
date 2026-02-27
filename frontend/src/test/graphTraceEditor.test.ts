import {describe, expect, it} from "vitest";
import {
  addPieRow,
  coerceInputValue,
  removePieRow,
  setTraceColor,
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
});
