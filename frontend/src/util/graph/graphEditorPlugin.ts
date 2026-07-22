import type {Component} from "solid-js";

export type ModularGraphTraceEditorProps = {
  trace: Record<string, unknown>;
  isDataEditingDisabled: boolean;
  onChange: (nextTrace: Record<string, unknown>) => void;
};

/** Extension point for graph modules whose trace data needs a custom editor. */
export type GraphEditorPlugin = {
  key: string;
  supportsTraceType: (traceType: string) => boolean;
  component: Component<ModularGraphTraceEditorProps>;
};
