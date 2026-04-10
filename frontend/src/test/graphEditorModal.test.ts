import { batch, createRenderEffect, createRoot, createSignal } from "solid-js";
import { renderToString } from "solid-js/web";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LocationGraph } from "../types/Types";

let latestTraceControlsProps: Record<string, unknown> | null = null;
let latestPieTraceEditorProps: Record<string, unknown> | null = null;

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
  default: () => null
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
    rowColors: string[];
    onUpdateColor: (rowIndex: number, colorHex: string) => void;
  };
};

describe("GraphEditorModal trace controls", () => {
  afterEach(() => {
    latestTraceControlsProps = null;
    latestPieTraceEditorProps = null;
  });

  it("passes the trace-control contract from modal to controls", () => {
    const dispose = renderModal();
    const props = getTraceControlsProps();

    expect(Array.isArray(props.traceOptions)).toBe(true);
    expect(props.selectedTraceIndex).toBe(0);
    expect(props.traceNameDraft).toBe("");
    expect(props.disableRenameTrace).toBe(true);
    expect(typeof props.onAddTrace).toBe("function");
    expect(typeof props.onChangeTraceName).toBe("function");
    expect(typeof props.onRenameTrace).toBe("function");

    dispose();
  });

  it("invokes add/rename handlers without relying on window.prompt", () => {
    const dispose = renderModal();
    const props = getTraceControlsProps();

    expect(() => {
      props.onAddTrace();
      props.onChangeTraceName("Renamed Trace");
      props.onRenameTrace();
    }).not.toThrow();

    dispose();
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
          color: "#2563eb",
          colors: ["#2563eb", "#16a34a"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const dispose = renderModal(pieGraph);
    await Promise.resolve();
    const pieProps = getPieTraceEditorProps();

    expect(latestTraceControlsProps).toBeNull();
    expect(pieProps.rowColors).toEqual(["#2563eb", "#16a34a"]);
    expect(() => pieProps.onUpdateColor(1, "#dc2626")).not.toThrow();

    dispose();
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
          color: "#2563eb",
          colors: ["#2563eb", "#16a34a"]
        }
      }],
      layout: null,
      config: null,
      style: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z"
    };

    const {dispose, setCurrentGraph, setIsOpen} = renderControllableModal(pieGraph);
    await Promise.resolve();

    expect(getPieTraceEditorProps()).toMatchObject({
      rowColors: ["#2563eb", "#16a34a"]
    });

    latestTraceControlsProps = null;
    latestPieTraceEditorProps = null;

    batch(() => {
      setCurrentGraph(undefined);
      setIsOpen(false);
    });
    await Promise.resolve();

    expect(() => getPieTraceEditorProps()).toThrowError("PieTraceEditor mock was not rendered.");
    const traceControls = getTraceControlsProps();
    expect(traceControls.traceOptions).toEqual([]);
    expect(traceControls.traceNameDraft).toBe("");

    dispose();
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
