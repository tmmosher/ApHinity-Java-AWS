import Dialog from "corvu/dialog";
import type { JSX } from "solid-js";
import {Show, createEffect, createMemo, createSignal} from "solid-js";
import {loadPlotlyModule} from "./Chart";
import CartesianTraceEditor from "./graph-editor/CartesianTraceEditor";
import PieTraceEditor from "./graph-editor/PieTraceEditor";
import TraceControls from "./graph-editor/TraceControls";
import type {LocationGraph} from "../types/Types";
import {
  type EditableGraphPayload,
  createEditableGraphPayload
} from "../util/graphEditor";
import {
  TRACE_COLOR_OPTIONS,
  addCartesianRow,
  addPieRow,
  buildTraceLabel,
  getTraceArray,
  getTraceColor,
  getTraceType,
  isRecord,
  removeCartesianRow,
  removePieRow,
  removeTraceWithPlotly,
  setTraceColor,
  updateCartesianX,
  updateCartesianY,
  updatePieLabel,
  updatePieValue
} from "../util/graphTraceEditor";
import type {TraceType} from "../util/graphTraceEditor";

type GraphEditorModalProps = {
  isOpen: boolean;
  graph: LocationGraph | undefined;
  canUndo: boolean;
  isSaving: boolean;
  onApply: (graphId: number, payload: EditableGraphPayload) => void;
  onUndo: () => void;
  onClose: () => void;
};

const TRACE_COLOR_VALUES = new Set(Object.values(TRACE_COLOR_OPTIONS));

export const GraphEditorModal = (props: GraphEditorModalProps) => {
  const [editablePayload, setEditablePayload] = createSignal<EditableGraphPayload>({
    data: [],
    layout: null,
    config: null,
    style: null
  });
  const [selectedTraceIndex, setSelectedTraceIndex] = createSignal(0);
  const [operationError, setOperationError] = createSignal("");
  const [isRemovingTrace, setIsRemovingTrace] = createSignal(false);

  const isBusy = () => props.isSaving || isRemovingTrace();

  createEffect(() => {
    if (!props.isOpen || !props.graph) {
      return;
    }

    setEditablePayload(createEditableGraphPayload(props.graph));
    setSelectedTraceIndex(0);
    setOperationError("");
  });

  createEffect(() => {
    const count = editablePayload().data.length;
    if (count === 0) {
      if (selectedTraceIndex() !== 0) {
        setSelectedTraceIndex(0);
      }
      return;
    }
    if (selectedTraceIndex() > count - 1) {
      setSelectedTraceIndex(count - 1);
    }
  });

  const traceOptions = createMemo(() =>
    editablePayload().data.map((entry, index) => {
      const trace = isRecord(entry) ? entry : {};
      return {
        index,
        label: buildTraceLabel(trace, index)
      };
    })
  );

  const selectedTrace = createMemo(() => {
    const trace = editablePayload().data[selectedTraceIndex()];
    return isRecord(trace) ? trace : undefined;
  });

  const selectedTraceType = createMemo(() => {
    const trace = selectedTrace();
    return trace ? getTraceType(trace) : null;
  });

  const selectedTraceColorValue = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return "";
    }
    const traceColor = getTraceColor(trace);
    if (!traceColor) {
      return "";
    }
    return TRACE_COLOR_VALUES.has(traceColor) ? traceColor : "";
  });

  const pieLabels = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return [] as unknown[];
    }
    return getTraceArray(trace, "labels");
  });

  const pieValues = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return [] as unknown[];
    }
    return getTraceArray(trace, "values");
  });

  const pieRowIndexes = createMemo(() =>
    Array.from({length: Math.max(pieLabels().length, pieValues().length)}, (_, index) => index)
  );

  const cartesianXValues = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return [] as unknown[];
    }
    return getTraceArray(trace, "x");
  });

  const cartesianYValues = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return [] as unknown[];
    }
    return getTraceArray(trace, "y");
  });

  const cartesianRowIndexes = createMemo(() =>
    Array.from({length: Math.max(cartesianXValues().length, cartesianYValues().length)}, (_, index) => index)
  );

  const updateSelectedTrace = (
    mutator: (trace: Record<string, unknown>) => Record<string, unknown>
  ) => {
    const index = selectedTraceIndex();
    setEditablePayload((current) => {
      const existingTrace = current.data[index];
      if (!isRecord(existingTrace)) {
        return current;
      }
      const nextData = current.data.slice();
      nextData[index] = mutator(existingTrace);
      return {
        ...current,
        data: nextData
      };
    });
    setOperationError("");
  };

  const applyTraceColor = (colorHex: string) => {
    const traceType = selectedTraceType();
    if (!traceType) {
      return;
    }

    updateSelectedTrace((trace) => setTraceColor(trace, traceType, colorHex));
  };

  const removeSelectedTrace = async () => {
    if (isBusy()) {
      return;
    }

    const traceIndex = selectedTraceIndex();
    const currentPayload = editablePayload();
    if (traceIndex < 0 || traceIndex >= currentPayload.data.length) {
      return;
    }

    setIsRemovingTrace(true);
    setOperationError("");

    try {
      const plotly = await loadPlotlyModule();
      const nextData = await removeTraceWithPlotly(plotly, currentPayload, traceIndex);

      setEditablePayload((payload) => ({
        ...payload,
        data: nextData
      }));
    } catch (error) {
      setOperationError(error instanceof Error ? error.message : "Unable to remove trace.");
    } finally {
      setIsRemovingTrace(false);
    }
  };

  const editorSectionByType: Record<TraceType, () => JSX.Element> = {
    pie: () => (
      <PieTraceEditor
        rowIndexes={pieRowIndexes()}
        labels={pieLabels()}
        values={pieValues()}
        isBusy={isBusy()}
        onAddRow={() => updateSelectedTrace((trace) => addPieRow(trace))}
        onUpdateLabel={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updatePieLabel(trace, rowIndex, rawValue))
        }
        onUpdateValue={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updatePieValue(trace, rowIndex, rawValue))
        }
        onRemoveRow={(rowIndex) =>
          updateSelectedTrace((trace) => removePieRow(trace, rowIndex))
        }
      />
    ),
    bar: () => (
      <CartesianTraceEditor
        heading="Bar"
        rowIndexes={cartesianRowIndexes()}
        xValues={cartesianXValues()}
        yValues={cartesianYValues()}
        isBusy={isBusy()}
        onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
        onUpdateX={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updateCartesianX(trace, rowIndex, rawValue))
        }
        onUpdateY={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updateCartesianY(trace, rowIndex, rawValue))
        }
        onRemoveRow={(rowIndex) =>
          updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex))
        }
      />
    ),
    scatter: () => (
      <CartesianTraceEditor
        heading="Scatter"
        rowIndexes={cartesianRowIndexes()}
        xValues={cartesianXValues()}
        yValues={cartesianYValues()}
        isBusy={isBusy()}
        onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
        onUpdateX={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updateCartesianX(trace, rowIndex, rawValue))
        }
        onUpdateY={(rowIndex, rawValue) =>
          updateSelectedTrace((trace) => updateCartesianY(trace, rowIndex, rawValue))
        }
        onRemoveRow={(rowIndex) =>
          updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex))
        }
      />
    )
  };

  const close = () => {
    if (isRemovingTrace()) {
      return;
    }
    setOperationError("");
    props.onClose();
  };

  const applyEdit = () => {
    if (isBusy()) {
      return;
    }

    const graph = props.graph;
    if (!graph) {
      return;
    }

    try {
      props.onApply(graph.id, editablePayload());
      setOperationError("");
      props.onClose();
    } catch (error) {
      setOperationError(error instanceof Error ? error.message : "Unable to apply graph edits.");
    }
  };

  return (
    <Dialog
      open={props.isOpen}
      onOpenChange={(open) => {
        if (!open) {
          close();
        }
      }}
    >
      <Dialog.Portal >
        <Dialog.Overlay
          class="fixed inset-0 z-50 bg-black/45 data-closed:pointer-events-none"
        />
        <Dialog.Content
          class="fixed inset-0 z-[60] m-auto flex h-[min(92vh,44rem)] w-[min(96vw,56rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-4 shadow-2xl data-closed:pointer-events-none md:p-5"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="space-y-1">
              <Dialog.Label id="graph-editor-title" class="text-lg font-semibold">
                Edit Graph Data
              </Dialog.Label>
              <Dialog.Description class="text-sm text-base-content/70">
                Graph: {props.graph?.name ?? "-"}
              </Dialog.Description>
            </div>
            <Dialog.Close class="btn btn-sm btn-ghost" disabled={isBusy()}>Close</Dialog.Close>
          </div>

          <div class="space-y-4 overflow-y-auto">
            <TraceControls
              traceOptions={traceOptions()}
              selectedTraceIndex={selectedTraceIndex()}
              selectedTraceColor={selectedTraceColorValue()}
              colorOptions={TRACE_COLOR_OPTIONS}
              disableTraceSelect={isBusy() || traceOptions().length === 0}
              disableColorSelect={isBusy() || !selectedTraceType()}
              disableRemoveTrace={isBusy() || !selectedTrace()}
              onSelectTrace={setSelectedTraceIndex}
              onApplyColor={applyTraceColor}
              onRemoveTrace={() => void removeSelectedTrace()}
            />

            {(() => {
              const trace = selectedTrace();
              if (!trace) {
                return (
                  <p class="rounded-lg border border-base-300 bg-base-200/40 p-3 text-sm text-base-content/70">
                    This graph has no traces to edit.
                  </p>
                );
              }

              const traceType = selectedTraceType();
              if (!traceType) {
                const rawType = typeof trace.type === "string" ? trace.type : "unknown";
                return (
                  <p class="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
                    Unsupported trace type "{rawType}". Supported editors: pie, bar, scatter.
                  </p>
                );
              }

              return editorSectionByType[traceType]();
            })()}
          </div>

          <Show when={operationError()}>
            <p class="text-sm text-error">{operationError()}</p>
          </Show>

          <div class="flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              class={"btn btn-sm " + (props.canUndo && !isBusy() ? "btn-outline" : "btn-disabled")}
              disabled={!props.canUndo || isBusy()}
              onClick={props.onUndo}
            >
              Undo
            </button>
            <button
              type="button"
              class={"btn btn-sm " + (isBusy() ? "btn-disabled" : "btn-primary")}
              disabled={isBusy()}
              onClick={applyEdit}
            >
              {props.isSaving ? "Saving..." : isRemovingTrace() ? "Updating..." : "Apply"}
            </button>
            <Dialog.Close class="btn btn-sm" disabled={isBusy()}>Close</Dialog.Close>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default GraphEditorModal;
