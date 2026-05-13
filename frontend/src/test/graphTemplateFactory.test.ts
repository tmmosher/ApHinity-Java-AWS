import {describe, expect, it} from "vitest";
import {
  INDICATOR_GAUGE_BACKGROUND_COLOR,
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
        bgcolor: INDICATOR_GAUGE_BACKGROUND_COLOR,
        bar: {
          color: "#1f77b4"
        },
        borderwidth: 0,
        steps: [
          {color: INDICATOR_GAUGE_BACKGROUND_COLOR, range: [0, 100]}
        ],
        threshold: {
          line: {
            color: "red",
            width: 2
          },
          thickness: 0.75,
          value: 0
        }
      }
    });
  });

  it("creates indicator traces with a dynamic threshold seed", () => {
    expect(createIndicatorTraceTemplate("Trace 1").gauge).toMatchObject({
      threshold: {
        value: 0
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
