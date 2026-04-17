import { batch, createRenderEffect, createRoot, createSignal } from "solid-js";
import { renderToString } from "solid-js/web";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LocationGraph } from "../types/Types";

let latestTraceControlsProps: Record<string, unknown> | null = null;
let latestPieTraceEditorProps: Record<string, unknown> | null = null;
let latestCartesianTraceEditorProps: Record<string, unknown> | null = null;

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
    latestCartesianTraceEditorProps = props;
    return null;
  }
}));

vi.mock("../components/graph-editor/PieTraceEditor", () => ({
  default: (props: Record<string, unknown>) => {
    latestPieTraceEditorProps = props;
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
    createRenderEffect(() => {
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
    });

    return dispose;
  });

const renderControllableModal = (graph?: LocationGraph) =>
  createRoot((dispose) => {
    const [isOpen, setIsOpen] = createSignal(true);
    const [currentGraph, setCurrentGraph] = createSignal<LocationGraph | undefined>(graph);

    createRenderEffect(() => {
      GraphEditorModal({
        isOpen: isOpen(),
        graph: currentGraph(),
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
    });

    return {
      dispose,
      setIsOpen,
      setCurrentGraph
    };
  });

const getTraceControlsProps = () => {
  if (!latestTraceControlsProps) {
    throw new Error("TraceControls mock was not rendered.");
  }
  return latestTraceControlsProps as {
    traceOptions: Array<{label: string}>;
    selectedTraceIndex: number;
    traceNameDraft: string;
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

const getCartesianTraceEditorProps = () => {
  if (!latestCartesianTraceEditorProps) {
    throw new Error("CartesianTraceEditor mock was not rendered.");
  }
  return latestCartesianTraceEditorProps as {
    xValues: unknown[];
    yValues: unknown[];
    xDrafts: Record<number, string>;
    yDrafts: Record<number, string>;
    yRangeMinDraft?: string;
    yRangeMaxDraft?: string;
    onUpdateX: (rowIndex: number, rawValue: string) => void;
    onUpdateY: (rowIndex: number, rawValue: string) => void;
    onUpdateYRangeMin: (rawValue: string) => void;
    onUpdateYRangeMax: (rawValue: string) => void;
  };
};

describe("GraphEditorModal trace controls", () => {
  afterEach(() => {
    latestTraceControlsProps = null;
    latestPieTraceEditorProps = null;
    latestCartesianTraceEditorProps = null;
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
      await Promise.resolve();
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
      await Promise.resolve();

      const pieProps = getPieTraceEditorProps();
      pieProps.onUpdateValue(0, "abc");
      await Promise.resolve();

      const updatedPieProps = getPieTraceEditorProps();
      expect(updatedPieProps.values).toEqual([3, 7]);
      expect(updatedPieProps.valueDrafts[0]).toBe("abc");
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
      await Promise.resolve();

      const cartesianProps = getCartesianTraceEditorProps();
      cartesianProps.onUpdateY(0, "abc");
      await Promise.resolve();

      const invalidProps = getCartesianTraceEditorProps();
      expect(invalidProps.yValues).toEqual([9]);
      expect(invalidProps.yDrafts[0]).toBe("abc");
    } finally {
      dispose();
    }
  });

  it("clears stale editor payload state when a deleted graph closes", async () => {
    const pieGraph: LocationGraph = {
      id: 16,
      name: "Cleanup Target",
      data: [{
        type: "pie",
        labels: ["Open", "Closed"],
        values: [68, 32],
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
    const barGraph: LocationGraph = {
      id: 18,
      name: "Fresh Target",
      data: [{
        type: "bar",
        name: "Bar Trace",
        x: ["Point 1"],
        y: [12]
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const {dispose, setCurrentGraph, setIsOpen} = renderControllableModal(pieGraph);
    try {
      await Promise.resolve();

      expect(getPieTraceEditorProps()).toMatchObject({
        rowColors: ["#1f77b4", "#2ca02c"]
      });

      batch(() => {
        setCurrentGraph(undefined);
        setIsOpen(false);
      });
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();

      latestTraceControlsProps = null;
      latestPieTraceEditorProps = null;
      batch(() => {
        setCurrentGraph(barGraph);
        setIsOpen(true);
      });
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();

      expect(() => getPieTraceEditorProps()).toThrowError("PieTraceEditor mock was not rendered.");
      const cartesianProps = getCartesianTraceEditorProps();
      expect(cartesianProps.xValues).toEqual(["Point 1"]);
      expect(cartesianProps.yValues).toEqual([12]);

      if (latestTraceControlsProps) {
        const traceControls = getTraceControlsProps();
        expect(traceControls.traceOptions).toEqual([{index: 0, label: "1. Bar Trace (bar)"}]);
        expect(traceControls.traceNameDraft).toBe("Bar Trace");
      }
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
