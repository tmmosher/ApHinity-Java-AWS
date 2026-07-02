import { createRoot } from "solid-js";
import { renderToString } from "solid-js/web";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LocationGraph } from "../types/Types";

let latestTraceControlsProps: Record<string, unknown> | null = null;
let latestPieTraceEditorProps: Record<string, unknown> | null = null;
let latestIndicatorTraceEditorProps: Record<string, unknown> | null = null;
let latestCartesianTraceEditorProps: Record<string, unknown> | null = null;
let latestTableTraceEditorProps: Record<string, unknown> | null = null;

vi.mock("corvu/dialog", () => {
  const Dialog = (props: { children: unknown }) => props.children;
  Dialog.Portal = (props: { children: unknown }) => props.children;
  Dialog.Overlay = () => null;
  Dialog.Content = (props: { children: unknown }) => props.children;
  Dialog.Label = (props: { children: unknown }) => props.children;
  Dialog.Description = (props: { children: unknown }) => props.children;
  Dialog.Close = (props: { children?: unknown }) => props.children ?? null;
  return { default: Dialog };
});

vi.mock("corvu/popover", () => {
  const Popover = (props: { children: (api: { open: boolean; setOpen: (open: boolean) => void }) => unknown }) =>
    props.children({ open: false, setOpen: () => undefined });
  Popover.Trigger = (props: { children?: unknown }) => props.children ?? null;
  Popover.Portal = (props: { children: unknown }) => props.children;
  Popover.Content = (props: { children: unknown }) => props.children;
  Popover.Label = (props: { children: unknown }) => props.children;
  Popover.Description = (props: { children: unknown }) => props.children;
  Popover.Close = (props: { children?: unknown }) => props.children ?? null;
  return { default: Popover };
});

vi.mock("../components/Chart", () => ({
  loadPlotlyModule: vi.fn()
}));

vi.mock("../components/graph-editor/CartesianTraceEditor", () => ({
  default: (props: Record<string, unknown>) => {
    latestCartesianTraceEditorProps = new Proxy({} as Record<string, unknown>, {
      get: (_target, property: string | symbol) => {
        if (typeof property !== "string") {
          return undefined;
        }
        const value = props[property];
        if (typeof value !== "function") {
          return value;
        }
        return (...args: unknown[]) => {
          const handler = props[property];
          return typeof handler === "function" ? handler(...args) : undefined;
        };
      }
    });
    return null;
  }
}));

vi.mock("../components/graph-editor/PieTraceEditor", () => ({
  default: (props: Record<string, unknown>) => {
    latestPieTraceEditorProps = props;
    return null;
  }
}));

vi.mock("../components/graph-editor/IndicatorTraceEditor", () => ({
  default: (props: Record<string, unknown>) => {
    latestIndicatorTraceEditorProps = props;
    return null;
  }
}));

vi.mock("../components/graph-editor/TableTraceEditor", () => ({
  default: (props: Record<string, unknown>) => {
    latestTableTraceEditorProps = new Proxy({} as Record<string, unknown>, {
      get: (_target, property: string | symbol) => {
        if (typeof property !== "string") {
          return undefined;
        }
        const value = props[property];
        if (typeof value !== "function") {
          return value;
        }
        return (...args: unknown[]) => {
          const handler = props[property];
          return typeof handler === "function" ? handler(...args) : undefined;
        };
      }
    });
    return null;
  }
}));

vi.mock("../components/graph-editor/TraceControls", () => ({
  default: (props: Record<string, unknown>) => {
    latestTraceControlsProps = props;
    return null;
  }
}));

import { GraphEditorModal } from "../components/graph-editor/GraphEditorModal";

const renderModal = (graph?: LocationGraph) =>
  createRoot((dispose) => {
    GraphEditorModal({
      isOpen: true,
      graph,
      canRenameGraph: false,
      canDeleteGraph: false,
      canUndo: false,
      isDeleting: false,
      isSaving: false,
      onApply: vi.fn(),
      onDeleteGraph: vi.fn().mockResolvedValue(undefined),
      onRenameGraph: vi.fn().mockResolvedValue(undefined),
      onUndo: vi.fn(),
      onClose: vi.fn()
    });

    return dispose;
  });

const renderModalWithDataEditing = (graph: LocationGraph | undefined, canEditData: boolean) =>
  createRoot((dispose) => {
    GraphEditorModal({
      isOpen: true,
      graph,
      canRenameGraph: false,
      canDeleteGraph: false,
      canEditData,
      canUndo: false,
      isDeleting: false,
      isSaving: false,
      onApply: vi.fn(),
      onDeleteGraph: vi.fn().mockResolvedValue(undefined),
      onRenameGraph: vi.fn().mockResolvedValue(undefined),
      onUndo: vi.fn(),
      onClose: vi.fn()
    });

    return dispose;
  });

const flushSolidUpdates = async () => {
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
};

const getTraceControlsProps = () => {
  if (!latestTraceControlsProps) {
    throw new Error("TraceControls mock was not rendered.");
  }
  return latestTraceControlsProps as {
    traceOptions: Array<{label: string}>;
    selectedTraceIndex: number;
    traceNameDraft: string;
    showColorSelect: boolean;
    disableColorSelect: boolean;
    disableRenameTrace: boolean;
    onAddTrace: () => void;
    onChangeTraceName: (nextName: string) => void;
    onRenameTrace: () => void;
  };
};

const getPieTraceEditorProps = () => {
  if (!latestPieTraceEditorProps) {
    throw new Error("PieTraceEditor mock was not rendered.");
  }
  return latestPieTraceEditorProps as {
    values: unknown[];
    valueDrafts: Record<number, string>;
    rowColors: string[];
    onUpdateValue: (rowIndex: number, rawValue: string) => void;
    onUpdateColor: (rowIndex: number, colorHex: string) => void;
  };
};

const getIndicatorTraceEditorProps = () => {
  if (!latestIndicatorTraceEditorProps) {
    throw new Error("IndicatorTraceEditor mock was not rendered.");
  }
  return latestIndicatorTraceEditorProps as {
    value: unknown;
    valueDraft?: string;
    color: string;
    onUpdateValue: (rawValue: string) => void;
    onUpdateColor: (colorHex: string) => void;
  };
};

const getTableTraceEditorProps = () => {
  if (!latestTableTraceEditorProps) {
    throw new Error("TableTraceEditor mock was not rendered.");
  }
  return latestTableTraceEditorProps as {
    headers: unknown[];
    columns: unknown[][];
    rowIndexes: number[];
    isDataEditingDisabled: boolean;
    onAddColumn: () => void;
    onRemoveColumn: (columnIndex: number) => void;
    onUpdateHeader: (columnIndex: number, rawValue: string) => void;
    onAddRow: () => void;
    onUpdateCell: (rowIndex: number, columnIndex: number, rawValue: string) => void;
    onRemoveRow: (rowIndex: number) => void;
  };
};

const getCartesianTraceEditorProps = () => {
  if (!latestCartesianTraceEditorProps) {
    throw new Error("CartesianTraceEditor mock was not rendered.");
  }
  return latestCartesianTraceEditorProps as {
    xValues: unknown[];
    yValues: unknown[];
    rowColors?: string[];
    colorOptions?: Record<string, string>;
    xLabel?: string;
    yLabel?: string;
    rangeLabel?: string;
    barOrientation?: "h" | "v";
    xDrafts: Record<number, string>;
    yDrafts: Record<number, string>;
    xInputMode?: "decimal";
    yInputMode?: "decimal";
    yRangeMinDraft?: string;
    yRangeMaxDraft?: string;
    isDataEditingDisabled: boolean;
    axisTitleControls?: Array<{
      key: string;
      label: string;
      value: string;
      placeholder: string;
      onUpdate: (rawValue: string) => void;
    }>;
    onUpdateBarOrientation?: (nextOrientation: "h" | "v") => void;
    onUpdateX: (rowIndex: number, rawValue: string) => void;
    onUpdateY: (rowIndex: number, rawValue: string) => void;
    onUpdateColor?: (rowIndex: number, colorHex: string) => void;
    onUpdateYRangeMin: (rawValue: string) => void;
    onUpdateYRangeMax: (rawValue: string) => void;
  };
};

describe("GraphEditorModal trace controls", () => {
  afterEach(() => {
    latestTraceControlsProps = null;
    latestPieTraceEditorProps = null;
    latestIndicatorTraceEditorProps = null;
    latestCartesianTraceEditorProps = null;
    latestTableTraceEditorProps = null;
  });

  it("passes the trace-control contract from modal to controls", () => {
    const dispose = renderModal();
    try {
      const props = getTraceControlsProps();

      expect(Array.isArray(props.traceOptions)).toBe(true);
      expect(props.selectedTraceIndex).toBe(0);
      expect(props.traceNameDraft).toBe("");
      expect(props.disableRenameTrace).toBe(true);
      expect(typeof props.onAddTrace).toBe("function");
      expect(typeof props.onChangeTraceName).toBe("function");
      expect(typeof props.onRenameTrace).toBe("function");
    } finally {
      dispose();
    }
  });

  it("invokes add/rename handlers without relying on window.prompt", () => {
    const dispose = renderModal();
    try {
      const props = getTraceControlsProps();

      expect(() => {
        props.onAddTrace();
        props.onChangeTraceName("Renamed Trace");
        props.onRenameTrace();
      }).not.toThrow();
    } finally {
      dispose();
    }
  });

  it("marks data controls disabled when graph data editing is not allowed", async () => {
    const barGraph: LocationGraph = {
      id: 14,
      name: "Projected",
      data: [{
        type: "bar",
        name: "Trace 1",
        orientation: "h",
        x: [3],
        y: ["Open"]
      }],
      layout: {xaxis: {title: "Value"}},
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModalWithDataEditing(barGraph, false);
    try {
      await flushSolidUpdates();

      const traceProps = getTraceControlsProps();
      const cartesianProps = getCartesianTraceEditorProps();
      expect(traceProps.disableRenameTrace).toBe(true);
      expect(cartesianProps.isDataEditingDisabled).toBe(true);
      expect(cartesianProps.axisTitleControls?.[0]?.label).toBe("Value axis title");
      expect(typeof cartesianProps.axisTitleControls?.[0]?.onUpdate).toBe("function");
    } finally {
      dispose();
    }
  });

  it("hides trace controls for pie graphs and passes row color editing props", async () => {
    const pieGraph: LocationGraph = {
      id: 15,
      name: "Breakdown",
      data: [{
        type: "pie",
        labels: ["Open", "Closed"],
        values: [3, 7],
        marker: {
          color: "#1f77b4",
          colors: ["#1f77b4", "#2ca02c"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(pieGraph);
    try {
      await flushSolidUpdates();
      const pieProps = getPieTraceEditorProps();

      expect(latestTraceControlsProps).toBeNull();
      expect(pieProps.rowColors).toEqual(["#1f77b4", "#2ca02c"]);
      expect(() => pieProps.onUpdateColor(1, "#d62728")).not.toThrow();
    } finally {
      dispose();
    }
  });

  it("stages invalid pie values in the modal instead of mutating graph data", async () => {
    const pieGraph: LocationGraph = {
      id: 16,
      name: "Breakdown",
      data: [{
        type: "pie",
        labels: ["Open", "Closed"],
        values: [3, 7],
        marker: {
          color: "#1f77b4",
          colors: ["#1f77b4", "#2ca02c"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(pieGraph);
    try {
      await flushSolidUpdates();

      const pieProps = getPieTraceEditorProps();
      pieProps.onUpdateValue(0, "abc");
      await flushSolidUpdates();

      const updatedPieProps = getPieTraceEditorProps();
      expect(updatedPieProps.values).toEqual([3, 7]);
      expect(updatedPieProps.valueDrafts[0]).toBe("abc");
    } finally {
      dispose();
    }
  });

  it("hides trace controls for indicator graphs and passes value editing props", async () => {
    const indicatorGraph: LocationGraph = {
      id: 18,
      name: "Resolution Percent",
      data: [{
        type: "indicator",
        name: "Trace 1",
        mode: "gauge+number",
        value: 68,
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
          bgcolor: "#6b728040",
          bar: {
            color: "#1f77b4"
          },
          borderwidth: 0,
          steps: [
            {color: "#6b728040", range: [0, 100]}
          ],
          threshold: {
            line: {
              color: "red",
              width: 2
            },
            thickness: 0.75,
            value: 68
          }
        }
      }],
      layout: {showlegend: false},
      config: {displayModeBar: false, responsive: false},
      style: {height: 160},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(indicatorGraph);
    try {
      await flushSolidUpdates();

      const indicatorProps = getIndicatorTraceEditorProps();

      expect(latestTraceControlsProps).toBeNull();
      expect(indicatorProps.value).toBe(68);
      expect(indicatorProps.color).toBe("#1f77b4");

      indicatorProps.onUpdateValue("abc");
      await flushSolidUpdates();

      const updatedIndicatorProps = getIndicatorTraceEditorProps();
      expect(updatedIndicatorProps.value).toBe(68);
      expect(updatedIndicatorProps.valueDraft).toBe("abc");

      indicatorProps.onUpdateValue("150");
      await flushSolidUpdates();

      const boundedIndicatorProps = getIndicatorTraceEditorProps();
      expect(boundedIndicatorProps.value).toBe(68);
      expect(boundedIndicatorProps.valueDraft).toBe("150");

      indicatorProps.onUpdateValue("1.");
      await flushSolidUpdates();

      const incompleteIndicatorProps = getIndicatorTraceEditorProps();
      expect(incompleteIndicatorProps.value).toBe(68);
      expect(incompleteIndicatorProps.valueDraft).toBe("1.");
    } finally {
      dispose();
    }
  });

  it("hides trace controls for table graphs and updates table cells", async () => {
    const tableGraph: LocationGraph = {
      id: 19,
      name: "Summary Table",
      data: [{
        type: "table",
        name: "Trace 1",
        header: {values: ["Metric", "Value"]},
        cells: {values: [["Open"], [3]]}
      }],
      layout: {meta: {aphinitySize: "double"}},
      config: {displayModeBar: false, responsive: false},
      style: {height: 640},
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(tableGraph);
    try {
      await flushSolidUpdates();
      const tableProps = getTableTraceEditorProps();

      expect(latestTraceControlsProps).toBeNull();
      expect(tableProps.headers).toEqual(["Metric", "Value"]);
      expect(tableProps.columns).toEqual([["Open"], [3]]);
      expect(tableProps.rowIndexes).toEqual([0]);

      expect(() => {
        tableProps.onUpdateHeader(1, "Count");
        tableProps.onAddRow();
        tableProps.onUpdateCell(0, 0, "Closed");
        tableProps.onRemoveRow(0);
      }).not.toThrow();
    } finally {
      dispose();
    }
  });

  it("rejects invalid cartesian values while the modal is open", async () => {
    const scatterGraph: LocationGraph = {
      id: 17,
      name: "Trend",
      data: [{
        type: "scatter",
        name: "Daily",
        x: ["2026-01-01"],
        y: [9]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(scatterGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      cartesianProps.onUpdateY(0, "abc");
      await flushSolidUpdates();

      const invalidProps = getCartesianTraceEditorProps();
      expect(invalidProps.yValues).toEqual([9]);
      expect(invalidProps.yDrafts[0]).toBe("abc");
    } finally {
      dispose();
    }
  });

  it("passes through and updates the selected cartesian value-axis title", async () => {
    const scatterGraph: LocationGraph = {
      id: 23,
      name: "Trend Title",
      data: [{
        type: "scatter",
        name: "Daily",
        x: ["2026-01-01"],
        y: [9]
      }],
      layout: {
        yaxis: {
          range: [0, 100],
          title: {text: "% Conformance", font: {size: 14}}
        }
      },
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(scatterGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      const yAxisControl = cartesianProps.axisTitleControls?.find((control) => control.key === "y");
      expect(yAxisControl?.label).toBe("y axis title");
      expect(yAxisControl?.value).toBe("% Conformance");

      yAxisControl?.onUpdate("Monthly pass rate");
      await flushSolidUpdates();
    } finally {
      dispose();
    }
  });

  it("passes through and updates bar value and category axis titles", async () => {
    const barGraph: LocationGraph = {
      id: 24,
      name: "Facility Counts",
      data: [{
        type: "bar",
        orientation: "h",
        name: "Open",
        x: [9],
        y: ["North"]
      }],
      layout: {
        xaxis: {title: {text: "Count"}},
        yaxis: {title: {text: "Facility"}}
      },
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(barGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      const valueAxisControl = cartesianProps.axisTitleControls?.find((control) => control.key === "value");
      const categoryAxisControl = cartesianProps.axisTitleControls?.find((control) => control.key === "category");
      expect(valueAxisControl?.label).toBe("Value axis title");
      expect(valueAxisControl?.value).toBe("Count");
      expect(categoryAxisControl?.label).toBe("Category axis title");
      expect(categoryAxisControl?.value).toBe("Facility");

      categoryAxisControl?.onUpdate("Building");
      await flushSolidUpdates();
    } finally {
      dispose();
    }
  });

  it("does not expose row color editing props for scatter traces", async () => {
    const scatterGraph: LocationGraph = {
      id: 30,
      name: "Trend",
      data: [{
        type: "scatter",
        name: "Daily",
        x: ["2026-01-01"],
        y: [9]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(scatterGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      expect(cartesianProps.rowColors).toBeUndefined();
      expect(cartesianProps.onUpdateColor).toBeUndefined();
    } finally {
      dispose();
    }
  });

  it("stages invalid cartesian x values while the modal is open", async () => {
    const scatterGraph: LocationGraph = {
      id: 20,
      name: "Trend X",
      data: [{
        type: "scatter",
        name: "Daily",
        x: [9],
        y: [12]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(scatterGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      cartesianProps.onUpdateX(0, "abc");
      await flushSolidUpdates();

      const invalidProps = getCartesianTraceEditorProps();
      expect(invalidProps.xValues).toEqual([9]);
      expect(invalidProps.xDrafts[0]).toBe("abc");
    } finally {
      dispose();
    }
  });

  it("swaps bar trace axes when the orientation changes", async () => {
    const barGraph: LocationGraph = {
      id: 19,
      name: "Counts",
      data: [{
        type: "bar",
        name: "Open",
        orientation: "h",
        x: [3, 7],
        y: ["North", "South"]
      }],
      layout: {
        xaxis: {title: {text: "Count"}},
        yaxis: {title: {text: "Facility"}}
      },
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(barGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      expect(cartesianProps.barOrientation).toBe("h");
      expect(cartesianProps.xLabel).toBe("Value");
      expect(cartesianProps.yLabel).toBe("Category");
      expect(cartesianProps.xInputMode).toBe("decimal");
      expect(cartesianProps.yInputMode).toBeUndefined();

      cartesianProps.onUpdateBarOrientation?.("v");
      await flushSolidUpdates();
    } finally {
      dispose();
    }
  });

  it("passes bar row color editing props and updates the selected bar row color", async () => {
    const barGraph: LocationGraph = {
      id: 29,
      name: "Buckets",
      data: [{
        type: "bar",
        name: "Trace 1",
        orientation: "h",
        x: [9, 6],
        y: ["North", "South"],
        marker: {
          color: "#1f77b4",
          colors: ["#1f77b4", "#2ca02c"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(barGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      expect(cartesianProps.rowColors).toEqual(["#1f77b4", "#2ca02c"]);
      expect(cartesianProps.colorOptions).toBeTruthy();

      cartesianProps.onUpdateColor?.(1, "#d62728");
      await flushSolidUpdates();
    } finally {
      dispose();
    }
  });

  it("hides the trace-level color control for bar traces", () => {
    const dispose = renderModal({
      id: 26,
      name: "Bucket colors",
      data: [{
        type: "bar",
        orientation: "h",
        x: [3, 7],
        y: ["North", "South"],
        marker: {
          color: "#1f77b4",
          colors: ["#1f77b4", "#2ca02c"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    });
    try {
      const traceControlsProps = getTraceControlsProps();

      expect(traceControlsProps.showColorSelect).toBe(false);
      expect(traceControlsProps.disableColorSelect).toBe(true);
    } finally {
      dispose();
    }
  });

  it("accepts string bucket values on the y-axis for horizontal bars", async () => {
    const barGraph: LocationGraph = {
      id: 21,
      name: "Counts",
      data: [{
        type: "bar",
        orientation: "h",
        x: [3],
        y: ["North"]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(barGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      cartesianProps.onUpdateY(0, "Irvine");
      await flushSolidUpdates();
    } finally {
      dispose();
    }
  });

  it("accepts string bucket values on the x-axis for vertical bars", async () => {
    const barGraph: LocationGraph = {
      id: 22,
      name: "Counts",
      data: [{
        type: "bar",
        orientation: "v",
        x: ["Irvine"],
        y: [3]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(barGraph);
    try {
      await flushSolidUpdates();

      const cartesianProps = getCartesianTraceEditorProps();
      cartesianProps.onUpdateX(0, "Irvine");
      await flushSolidUpdates();

      const updatedProps = getCartesianTraceEditorProps();
      expect(updatedProps.xValues).toEqual(["Irvine"]);
      expect(updatedProps.xDrafts[0]).toBeUndefined();
    } finally {
      dispose();
    }
  });

  it("keeps the delete confirmation rendered while a delete is in progress", () => {
    const html = renderToString(() =>
      GraphEditorModal({
        isOpen: true,
        graph: undefined,
        canRenameGraph: false,
        canDeleteGraph: false,
        canUndo: false,
        isDeleting: true,
        isSaving: false,
        onApply: vi.fn(),
        onDeleteGraph: vi.fn().mockResolvedValue(undefined),
        onRenameGraph: vi.fn().mockResolvedValue(undefined),
        onUndo: vi.fn(),
        onClose: vi.fn()
      })
    );

    expect(html).toContain("Delete Graph");
    expect(html).toContain("Deleting...");
  });
});
