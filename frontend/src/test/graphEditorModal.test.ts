import { createRenderEffect, createRoot } from "solid-js";
import { afterEach, describe, expect, it, vi } from "vitest";

let latestTraceControlsProps: Record<string, unknown> | null = null;

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

vi.mock("../components/Chart", () => ({
  loadPlotlyModule: vi.fn()
}));

vi.mock("../components/graph-editor/CartesianTraceEditor", () => ({
  default: () => null
}));

vi.mock("../components/graph-editor/PieTraceEditor", () => ({
  default: () => null
}));

vi.mock("../components/graph-editor/TraceControls", () => ({
  default: (props: Record<string, unknown>) => {
    latestTraceControlsProps = props;
    return null;
  }
}));

import { GraphEditorModal } from "../components/graph-editor/GraphEditorModal";

const renderModal = () =>
  createRoot((dispose) => {
    createRenderEffect(() => {
      GraphEditorModal({
        isOpen: true,
        graph: undefined,
        canUndo: false,
        isSaving: false,
        onApply: vi.fn(),
        onUndo: vi.fn(),
        onClose: vi.fn()
      });
    });

    return dispose;
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

describe("GraphEditorModal trace controls", () => {
  afterEach(() => {
    latestTraceControlsProps = null;
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
});
