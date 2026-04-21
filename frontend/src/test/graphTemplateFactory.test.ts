import {describe, expect, it} from "vitest";
import {
  INDICATOR_VALUE_MAX,
  INDICATOR_VALUE_MIN,
  createIndicatorTraceTemplate,
  isIncompleteNumericInput,
  parseIndicatorValueInput
} from "../util/graph/graphTemplateFactory";

describe("graphTemplateFactory", () => {
  it("creates indicator traces with the shared gauge contract", () => {
    expect(createIndicatorTraceTemplate("Trace 1")).toEqual({
      type: "indicator",
      name: "Trace 1",
      mode: "gauge+number",
      value: 0,
      number: {
        suffix: "%",
        font: {
          size: 22
        }
      },
      gauge: {
        shape: "angular",
        axis: {
          range: [INDICATOR_VALUE_MIN, INDICATOR_VALUE_MAX]
        },
        bar: {
          color: "#1f77b4"
        },
        borderwidth: 0,
        steps: [
          {color: "#80000030", range: [0, 30]},
          {color: "#FF000030", range: [30, 60]},
          {color: "#FFFF0030", range: [60, 90]},
          {color: "#00800030", range: [90, 100]}
        ]
      }
    });
  });

  it("parses only complete indicator values within the allowed bounds", () => {
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MIN))).toBe(INDICATOR_VALUE_MIN);
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MAX))).toBe(INDICATOR_VALUE_MAX);
    expect(parseIndicatorValueInput(" 68 ")).toBe(68);
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MIN - 1))).toBeNull();
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MAX + 1))).toBeNull();
    expect(parseIndicatorValueInput("1.")).toBeNull();
    expect(parseIndicatorValueInput("")).toBeNull();
  });

  it("treats intermediate numeric strings as incomplete drafts", () => {
    expect(isIncompleteNumericInput("")).toBe(true);
    expect(isIncompleteNumericInput("-")).toBe(true);
    expect(isIncompleteNumericInput("1.")).toBe(true);
    expect(isIncompleteNumericInput("68")).toBe(false);
  });
});
