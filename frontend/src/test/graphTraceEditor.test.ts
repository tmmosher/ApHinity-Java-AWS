import {describe, expect, it} from "vitest";
import {
  AUTO_SIZE_TRACE_FLAG,
  addCartesianRow,
  addPieRow,
  addTableColumn,
  addTableRow,
  coerceInputValue,
  createTrace,
  getBarOrientation,
  getBarRowColor,
  getCartesianAxisValueMode,
  getPieRowColor,
  getTraceCartesianAxisTitle,
  getTraceYAxisRange,
  getTraceYAxisTitle,
  isAutoSizingPieTrace,
  parseNumericInput,
  removeCartesianRow,
  removePieRow,
  removeTableColumn,
  removeTableRow,
  renameTrace,
  setBarOrientation,
  setBarRowColor,
  setPieRowColor,
  getTraceColor,
  setTraceColor,
  swapCartesianLayoutAxes,
  updateTraceCartesianAxisTitle,
  updateCartesianX,
  updateCartesianY,
  updateTraceYAxisTitle,
  updateTraceYAxisRange,
  updatePieValue,
  updateTableCell,
  updateTableHeader,
  updateIndicatorValue
} from "../util/graph/graphTraceEditor";
import {
  INDICATOR_GAUGE_BACKGROUND_COLOR,
  INDICATOR_VALUE_MAX,
  INDICATOR_VALUE_MIN,
  parseIndicatorValueInput
} from "../util/graph/graphTemplateFactory";

describe("graphTraceEditor", () => {
  it("keeps intermediate numeric input instead of coercing to zero", () => {
    expect(coerceInputValue("-", 3, true)).toBe("-");
    expect(coerceInputValue(".", 3, true)).toBe(".");
    expect(coerceInputValue("1.", 3, true)).toBe("1.");
  });

  it("parses only complete numeric graph inputs", () => {
    expect(parseNumericInput("42")).toBe(42);
    expect(parseNumericInput("  3.5 ")).toBe(3.5);
    expect(parseNumericInput("abc")).toBeNull();
    expect(parseNumericInput("1.")).toBeNull();
    expect(parseNumericInput("-")).toBeNull();
  });

  it("syncs pie marker colors when rows are added and removed", () => {
    const baseTrace: Record<string, unknown> = {
      type: "pie",
      labels: ["Open"],
      values: [2],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4"]
      }
    };

    const withAddedRow = addPieRow(baseTrace);
    const addedColors = (withAddedRow.marker as {colors: unknown[]}).colors;
    expect(addedColors).toHaveLength(2);

    const withRemovedRow = removePieRow(withAddedRow, 0);
    const removedColors = (withRemovedRow.marker as {colors: unknown[]}).colors;
    expect(removedColors).toHaveLength(1);
  });

  it("updates only the selected pie row color", () => {
    const trace: Record<string, unknown> = {
      type: "pie",
      labels: ["Open", "Closed"],
      values: [2, 5],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4", "#2ca02c"]
      }
    };

    const nextTrace = setPieRowColor(trace, 1, "#d62728");

    expect(getPieRowColor(nextTrace, 0)).toBe("#1f77b4");
    expect(getPieRowColor(nextTrace, 1)).toBe("#d62728");
    expect((nextTrace.marker as {colors: string[]}).colors).toEqual(["#1f77b4", "#d62728"]);
    expect((nextTrace.marker as {color: string}).color).toBe("#1f77b4");
  });

  it("updates only the selected bar row color", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "h",
      x: [2, 5],
      y: ["North", "South"],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4", "#2ca02c"]
      }
    };

    const nextTrace = setBarRowColor(trace, 1, "#d62728");

    expect(getBarRowColor(nextTrace, 0)).toBe("#1f77b4");
    expect(getBarRowColor(nextTrace, 1)).toBe("#d62728");
    expect((nextTrace.marker as {colors: string[]}).colors).toEqual(["#1f77b4", "#d62728"]);
    expect((nextTrace.marker as {color: string[]}).color).toEqual(["#1f77b4", "#d62728"]);
  });

  it("applies a trace-level bar color to every row", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "v",
      x: ["North", "South"],
      y: [2, 5],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4", "#2ca02c"]
      }
    };

    const nextTrace = setTraceColor(trace, "bar", "#d62728");

    expect((nextTrace.marker as {color: string[]}).color).toEqual(["#d62728", "#d62728"]);
    expect((nextTrace.marker as {colors: string[]}).colors).toEqual(["#d62728", "#d62728"]);
    expect(getBarRowColor(nextTrace, 0)).toBe("#d62728");
    expect(getBarRowColor(nextTrace, 1)).toBe("#d62728");
  });

  it("applies scatter color to marker and line", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      x: [1, 2],
      y: [3, 4]
    };

    const nextTrace = setTraceColor(trace, "scatter", "#d62728");
    expect((nextTrace.marker as {color: string}).color).toBe("#d62728");
    expect((nextTrace.line as {color: string}).color).toBe("#d62728");
  });

  it("applies pie color to the rendered marker color array", () => {
    const trace: Record<string, unknown> = {
      type: "pie",
      labels: ["Open", "Closed"],
      values: [3, 7],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4", "#2ca02c"]
      }
    };

    const nextTrace = setTraceColor(trace, "pie", "#d62728");

    expect((nextTrace.marker as {color: string}).color).toBe("#d62728");
    expect((nextTrace.marker as {colors: string[]}).colors).toEqual(["#d62728", "#d62728"]);
  });

  it("applies indicator color to the gauge bar", () => {
    const trace: Record<string, unknown> = {
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bgcolor: INDICATOR_GAUGE_BACKGROUND_COLOR,
        bar: {color: "#1f77b4"},
        borderwidth: 0,
        steps: [
          {color: INDICATOR_GAUGE_BACKGROUND_COLOR, range: [0, 100]}
        ]
      }
    };

    const nextTrace = setTraceColor(trace, "indicator", "#d62728");

    expect(getTraceColor(nextTrace)).toBe("#d62728");
    expect(nextTrace.gauge).toEqual({
      shape: "angular",
      axis: {range: [0, 100]},
      bgcolor: INDICATOR_GAUGE_BACKGROUND_COLOR,
      bar: {color: "#d62728"},
      borderwidth: 0,
      steps: [
        {color: INDICATOR_GAUGE_BACKGROUND_COLOR, range: [0, 100]}
      ]
    });
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

  it("detects pie traces marked as auto-sizing", () => {
    expect(isAutoSizingPieTrace({
      type: "pie",
      [AUTO_SIZE_TRACE_FLAG]: true
    })).toBe(true);

    expect(isAutoSizingPieTrace({
      type: "pie",
      [AUTO_SIZE_TRACE_FLAG]: false
    })).toBe(false);
  });

  it("checks auto-sizing traces while keeping current pie value behavior", () => {
    const trace: Record<string, unknown> = {
      type: "pie",
      [AUTO_SIZE_TRACE_FLAG]: true,
      labels: ["fill", "rest"],
      values: [60, 40]
    };

    const nextTrace = updatePieValue(trace, 0, "70");
    expect((nextTrace.values as unknown[])[0]).toBe(70);
    expect((nextTrace.values as unknown[])[1]).toBe(40);
  });

  it("creates a blank scatter trace using the selected trace type and default naming", () => {
    const nextTrace = createTrace("scatter", 2);

    expect(nextTrace).toEqual({
      x: [],
      y: [],
      line: {color: "#1f77b4", width: 2},
      mode: "lines+markers",
      name: "Trace 3",
      type: "scatter",
      marker: {size: 6}
    });
  });

  it("creates a blank bar trace using the selected trace type and default naming", () => {
    const nextTrace = createTrace("bar", 0);

    expect(nextTrace).toEqual({
      type: "bar",
      name: "Trace 1",
      orientation: "h",
      x: [],
      y: [],
      marker: {color: "#1f77b4"}
    });
  });

  it("swaps bar trace values when the orientation changes", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "h",
      xaxis: "x2",
      yaxis: "y3",
      x: [3, 5],
      y: ["Jan", "Feb"]
    };

    const vertical = setBarOrientation(trace, "v");
    expect(getBarOrientation(vertical)).toBe("v");
    expect(vertical.xaxis).toBe("x3");
    expect(vertical.yaxis).toBe("y2");
    expect(vertical.x).toEqual(["Jan", "Feb"]);
    expect(vertical.y).toEqual([3, 5]);

    const horizontal = setBarOrientation(vertical, "h");
    expect(getBarOrientation(horizontal)).toBe("h");
    expect(horizontal.xaxis).toBe("x2");
    expect(horizontal.yaxis).toBe("y3");
    expect(horizontal.x).toEqual([3, 5]);
    expect(horizontal.y).toEqual(["Jan", "Feb"]);
  });

  it("derives numeric and categorical bar axes from the orientation", () => {
    expect(getCartesianAxisValueMode({
      type: "bar",
      orientation: "h",
      x: [3],
      y: ["North"]
    }, "x")).toBe("numeric");
    expect(getCartesianAxisValueMode({
      type: "bar",
      orientation: "h",
      x: [3],
      y: ["North"]
    }, "y")).toBe("categorical");
    expect(getCartesianAxisValueMode({
      type: "bar",
      orientation: "v",
      x: ["North"],
      y: [3]
    }, "x")).toBe("categorical");
    expect(getCartesianAxisValueMode({
      type: "bar",
      orientation: "v",
      x: ["North"],
      y: [3]
    }, "y")).toBe("numeric");
  });

  it("reads and updates the selected trace value-axis title", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      x: [1],
      y: [2]
    };
    const layout: Record<string, unknown> = {
      yaxis: {
        range: [0, 100],
        title: {
          text: "% Conformance",
          font: {size: 14}
        }
      }
    };

    expect(getTraceYAxisTitle(layout, trace)).toBe("% Conformance");

    expect(updateTraceYAxisTitle(layout, trace, "Monthly pass rate")).toEqual({
      yaxis: {
        range: [0, 100],
        title: {
          text: "Monthly pass rate",
          font: {size: 14}
        }
      }
    });
  });

  it("reads and updates the horizontal bar value-axis title from xaxis", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "h",
      x: [3, 7],
      y: ["North", "South"]
    };
    const layout: Record<string, unknown> = {
      xaxis: {
        range: [0, 10],
        title: {
          text: "Count",
          font: {size: 14}
        }
      },
      yaxis: {
        title: {
          text: "Facility"
        }
      }
    };

    expect(getTraceYAxisTitle(layout, trace)).toBe("Count");

    expect(updateTraceYAxisTitle(layout, trace, "Sample count")).toEqual({
      xaxis: {
        range: [0, 10],
        title: {
          text: "Sample count",
          font: {size: 14}
        }
      },
      yaxis: {
        title: {
          text: "Facility"
        }
      }
    });
  });

  it("reads and updates cartesian x-axis titles", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      x: ["2026-01-01"],
      y: [2]
    };
    const layout: Record<string, unknown> = {
      xaxis: {
        type: "date",
        title: {
          text: "Observed date",
          standoff: 8
        }
      },
      yaxis: {
        title: "Count"
      }
    };

    expect(getTraceCartesianAxisTitle(layout, trace, "x")).toBe("Observed date");

    expect(updateTraceCartesianAxisTitle(layout, trace, "x", "Sample date")).toEqual({
      xaxis: {
        type: "date",
        title: {
          text: "Sample date",
          standoff: 8
        }
      },
      yaxis: {
        title: "Count"
      }
    });
  });

  it("removes an empty cartesian axis title without dropping other axis settings", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "v",
      x: ["North"],
      y: [3]
    };

    expect(updateTraceCartesianAxisTitle({
      xaxis: {
        tickangle: -30,
        title: "Facility"
      },
      yaxis: {
        title: "Count"
      }
    }, trace, "x", "   ")).toEqual({
      xaxis: {
        tickangle: -30
      },
      yaxis: {
        title: "Count"
      }
    });
  });

  it("removes an empty value-axis title without dropping other axis settings", () => {
    const trace: Record<string, unknown> = {
      type: "scatter",
      x: [1],
      y: [2]
    };

    expect(updateTraceYAxisTitle({
      yaxis: {
        range: [0, 100],
        title: "Legacy title"
      }
    }, trace, "   ")).toEqual({
      yaxis: {
        range: [0, 100]
      }
    });
  });

  it("preserves categorical bucket text and coerces numeric bar values by axis mode", () => {
    const horizontal = updateCartesianY(
      {
        type: "bar",
        orientation: "h",
        x: [0],
        y: [""]
      },
      0,
      "Irvine",
      "categorical"
    );
    expect(horizontal.y).toEqual(["Irvine"]);

    const vertical = updateCartesianX(
      {
        type: "bar",
        orientation: "h",
        x: [""],
        y: [""]
      },
      0,
      "5",
      "numeric"
    );
    expect(vertical.x).toEqual([5]);
  });

  it("keeps bar row colors aligned when rows are added and removed", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "h",
      x: [2],
      y: ["North"],
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4"]
      }
    };

    const withAddedRow = addCartesianRow(trace);
    expect((withAddedRow.marker as {color: string[]}).color).toEqual(["#1f77b4", "#1f77b4"]);
    expect((withAddedRow.marker as {colors: string[]}).colors).toEqual(["#1f77b4", "#1f77b4"]);

    const recolored = setBarRowColor(withAddedRow, 1, "#d62728");
    const withRemovedRow = removeCartesianRow(recolored, 0);
    expect((withRemovedRow.marker as {colors: string[]}).colors).toEqual(["#d62728"]);
    expect((withRemovedRow.marker as {color: string[]}).color).toEqual(["#d62728"]);
  });

  it("updates table headers, columns, rows, and cells", () => {
    const trace: Record<string, unknown> = {
      type: "table",
      name: "Trace 1",
      header: {values: ["Metric", "Value"]},
      cells: {values: [["Open"], [3]]}
    };

    const renamed = updateTableHeader(trace, 1, "Count");
    expect((renamed.header as {values: unknown[]}).values).toEqual(["Metric", "Count"]);

    const withRow = addTableRow(renamed);
    const withCell = updateTableCell(
      updateTableCell(withRow, 1, 0, "Closed"),
      1,
      1,
      "7"
    );
    expect((withCell.cells as {values: unknown[][]}).values).toEqual([["Open", "Closed"], [3, "7"]]);

    const withColumn = addTableColumn(withCell);
    expect((withColumn.header as {values: unknown[]}).values).toEqual(["Metric", "Count", "Column 3"]);
    expect((withColumn.cells as {values: unknown[][]}).values).toEqual([["Open", "Closed"], [3, "7"], ["", ""]]);

    const withoutColumn = removeTableColumn(withColumn, 2);
    const withoutRow = removeTableRow(withoutColumn, 0);
    expect((withoutRow.header as {values: unknown[]}).values).toEqual(["Metric", "Count"]);
    expect((withoutRow.cells as {values: unknown[][]}).values).toEqual([["Closed"], ["7"]]);
  });

  it("creates pie traces with donut defaults", () => {
    const nextTrace = createTrace("pie", 0);

    expect(nextTrace).toEqual({
      type: "pie",
      name: "Trace 1",
      hole: 0.72,
      sort: false,
      labels: ["fill"],
      values: [30],
      textinfo: "none",
      direction: "clockwise",
      hovertemplate: "%{label}: %{value}<extra></extra>",
      marker: {
        color: "#1f77b4",
        colors: ["#1f77b4"]
      }
    });
  });

  it("creates indicator traces with gauge defaults", () => {
    const nextTrace = createTrace("indicator", 0);

    expect(nextTrace).toEqual({
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
          range: [0, 100]
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

  it("parses only bounded indicator values", () => {
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MIN))).toBe(INDICATOR_VALUE_MIN);
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MAX))).toBe(INDICATOR_VALUE_MAX);
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MIN - 1))).toBeNull();
    expect(parseIndicatorValueInput(String(INDICATOR_VALUE_MAX + 1))).toBeNull();
    expect(parseIndicatorValueInput("1.")).toBeNull();
  });

  it("keeps the previous indicator value when the input is outside the allowed range", () => {
    const trace: Record<string, unknown> = {
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        borderwidth: 0
      }
    };

    expect(updateIndicatorValue(trace, "150")).toEqual(trace);
    expect(updateIndicatorValue(trace, "-5")).toEqual(trace);
    expect(updateIndicatorValue(trace, "1.")).toEqual(trace);
  });

  it("syncs the indicator threshold value when the indicator value changes", () => {
    const trace: Record<string, unknown> = {
      type: "indicator",
      value: 68,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        borderwidth: 0,
        threshold: {
          line: {color: "red", width: 2},
          thickness: 0.75,
          value: 90
        }
      }
    };

    expect(updateIndicatorValue(trace, "72")).toEqual({
      type: "indicator",
      value: 72,
      gauge: {
        shape: "angular",
        axis: {range: [0, 100]},
        bar: {color: "#1f77b4"},
        borderwidth: 0,
        threshold: {
          line: {color: "red", width: 2},
          thickness: 0.75,
          value: 72
        }
      }
    });
  });

  it("renames traces and clears names when blank input is provided", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      name: "Current Name",
      x: ["Point 1"],
      y: [1]
    };

    const renamed = renameTrace(trace, "  Throughput  ");
    expect(renamed.name).toBe("Throughput");

    const unnamed = renameTrace(renamed, "   ");
    expect(unnamed.name).toBeUndefined();
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

  it("targets the numeric axis key for horizontal bar traces and preserves axis settings", () => {
    const trace: Record<string, unknown> = {
      type: "bar",
      orientation: "h",
      xaxis: "x2",
      x: [4, 5],
      y: ["A", "B"]
    };

    const layout = updateTraceYAxisRange(
      {
        xaxis2: {
          title: {text: "Secondary Axis"}
        }
      },
      trace,
      1,
      "42"
    );

    expect(layout).toEqual({
      xaxis2: {
        title: {text: "Secondary Axis"},
        range: [null, 42]
      }
    });
  });

  it("swaps cartesian axis layout settings when a bar chart flips orientation", () => {
    expect(swapCartesianLayoutAxes({
      xaxis: {title: {text: "Count"}},
      yaxis: {title: {text: "Facility"}},
      margin: {l: 10}
    })).toEqual({
      xaxis: {title: {text: "Facility"}},
      yaxis: {title: {text: "Count"}},
      margin: {l: 10}
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
