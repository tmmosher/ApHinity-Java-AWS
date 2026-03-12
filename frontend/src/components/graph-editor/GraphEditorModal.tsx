import Dialog from "corvu/dialog";
import Popover from "corvu/popover";
import {Match, Show, Switch, createEffect, createMemo, createSignal, on} from "solid-js";
import {loadPlotlyModule} from "../Chart";
import CartesianTraceEditor from "./CartesianTraceEditor";
import PieTraceEditor from "./PieTraceEditor";
import TraceControls from "./TraceControls";
import type {LocationGraph} from "../../types/Types";
import {
  type EditableGraphPayload,
  createEditableGraphPayload
} from "../../util/graph/graphEditor";
import {
  TRACE_COLOR_OPTIONS,
  addCartesianRow,
  addPieRow,
  buildTraceLabel,
  createTrace,
  getTraceArray,
  getTraceColor,
  getTraceType,
  getTraceYAxisRange,
  isRecord,
  removeCartesianRow,
  removePieRow,
  removeTraceWithPlotly,
  renameTrace,
  setTraceColor,
  updateTraceYAxisRange,
  updateCartesianX,
  updateCartesianY,
  updatePieLabel,
  updatePieValue
} from "../../util/graph/graphTraceEditor";

type GraphEditorModalProps = {
  isOpen: boolean;
  graph: LocationGraph | undefined;
  canRenameGraph: boolean;
  canUndo: boolean;
  isSaving: boolean;
  onApply: (graphId: number, payload: EditableGraphPayload) => void;
  onRenameGraph: (graphId: number, name: string) => Promise<void>;
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
  const [traceNameDraft, setTraceNameDraft] = createSignal("");
  const [graphNameDraft, setGraphNameDraft] = createSignal("");
  const [operationError, setOperationError] = createSignal("");
  const [renameError, setRenameError] = createSignal("");
  const [isRemovingTrace, setIsRemovingTrace] = createSignal(false);
  const [isSavingRename, setIsSavingRename] = createSignal(false);
  const [isRenamePopoverOpen, setIsRenamePopoverOpen] = createSignal(false);

  const isBusy = () => props.isSaving || isRemovingTrace() || isSavingRename();

  createEffect(on(() => [props.isOpen, props.graph?.id] as const, ([isOpen, graphId]) => {
    if (!isOpen || graphId === undefined || !props.graph) {
      return;
    }

    setEditablePayload(createEditableGraphPayload(props.graph));
    setGraphNameDraft(props.graph.name);
    setSelectedTraceIndex(0);
    setTraceNameDraft("");
    setOperationError("");
    setRenameError("");
    setIsRenamePopoverOpen(false);
  }));

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

  createEffect(() => {
    const trace = selectedTrace();
    const traceName = trace && typeof trace.name === "string" ? trace.name : "";
    setTraceNameDraft(traceName);
  });

  const selectedTraceType = createMemo(() => {
    const trace = selectedTrace();
    return trace ? getTraceType(trace) : null;
  });

  const unsupportedTraceType = createMemo(() => {
    const trace = selectedTrace();
    return trace && typeof trace.type === "string" ? trace.type : "unknown";
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

  const selectedTraceYAxisRange = createMemo<[unknown, unknown]>(() => {
    const trace = selectedTrace();
    if (!trace) {
      return ["", ""];
    }
    return getTraceYAxisRange(editablePayload().layout ?? null, trace);
  });

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

  const updateSelectedTraceYRange = (boundIndex: 0 | 1, rawValue: string) => {
    const index = selectedTraceIndex();
    setEditablePayload((current) => {
      const existingTrace = current.data[index];
      if (!isRecord(existingTrace)) {
        return current;
      }
      const nextLayout = updateTraceYAxisRange(
        current.layout ?? null,
        existingTrace,
        boundIndex,
        rawValue
      );
      return {
        ...current,
        layout: nextLayout
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

  const addNewTrace = () => {
    if (isBusy()) {
      return;
    }

    let nextTraceIndex = 0;
    let nextTraceName = "";

    setEditablePayload((current) => {
      const selectedEntry = current.data[selectedTraceIndex()];
      const preferredType = isRecord(selectedEntry) ? getTraceType(selectedEntry) : null;
      const nextTrace = createTrace(preferredType, current.data.length);
      const nextData = [...current.data, nextTrace];

      nextTraceIndex = nextData.length - 1;
      nextTraceName = typeof nextTrace.name === "string" ? nextTrace.name : "";

      return {
        ...current,
        data: nextData
      };
    });

    setSelectedTraceIndex(nextTraceIndex);
    setTraceNameDraft(nextTraceName);
    setOperationError("");
  };

  const renameSelectedTrace = () => {
    if (isBusy()) {
      return;
    }

    const currentTrace = selectedTrace();
    if (!currentTrace) {
      return;
    }

    updateSelectedTrace((trace) => renameTrace(trace, traceNameDraft()));
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

  const saveGraphRename = async (): Promise<boolean> => {
    if (isBusy()) {
      return false;
    }

    const graph = props.graph;
    if (!graph) {
      return false;
    }

    const normalizedName = graphNameDraft().trim();
    if (!normalizedName) {
      setRenameError("Graph name is required.");
      return false;
    }

    setRenameError("");
    setIsSavingRename(true);

    try {
      await props.onRenameGraph(graph.id, normalizedName);
      setGraphNameDraft(normalizedName);
      return true;
    } catch (error) {
      setRenameError(error instanceof Error ? error.message : "Unable to save graph name.");
      return false;
    } finally {
      setIsSavingRename(false);
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
            <div class="flex items-start gap-2">
              <Show when={props.canRenameGraph}>
                <Popover
                  placement="bottom-end"
                  open={isRenamePopoverOpen()}
                  onOpenChange={(open) => {
                    setIsRenamePopoverOpen(open);
                    if (open) {
                      setGraphNameDraft(props.graph?.name || "");
                      setRenameError("");
                    }
                  }}
                >
                  {(popover) => (
                    <>
                      <Popover.Trigger
                        class={"btn btn-sm " + (isBusy() ? "btn-disabled" : "btn-outline")}
                        disabled={isBusy()}
                      >
                        Rename
                      </Popover.Trigger>
                      <Popover.Portal forceMount>
                        <Popover.Content
                          forceMount
                          class="z-[80] w-[min(92vw,20rem)] rounded-xl border border-base-300 bg-base-100 p-4 shadow-xl"
                          style={{"pointer-events": popover.open ? "auto" : "none"}}
                        >
                          <div class="space-y-3">
                            <div class="space-y-1">
                              <Popover.Label class="text-sm font-semibold">Rename Graph</Popover.Label>
                              <Popover.Description class="text-xs text-base-content/70">
                                Update the graph title and save it to the server.
                              </Popover.Description>
                            </div>
                            <label class="form-control w-full">
                              <span class="label-text text-xs">Graph name</span>
                              <input
                                type="text"
                                class="input input-bordered input-sm w-full"
                                value={graphNameDraft()}
                                disabled={isBusy()}
                                onInput={(event) => {
                                  setGraphNameDraft(event.currentTarget.value);
                                  if (renameError()) {
                                    setRenameError("");
                                  }
                                }}
                              />
                            </label>
                            <Show when={renameError()}>
                              <p class="text-xs text-error">{renameError()}</p>
                            </Show>
                            <div class="flex items-center justify-end gap-2">
                              <Popover.Close class="btn btn-ghost btn-sm" disabled={isBusy()}>
                                Cancel
                              </Popover.Close>
                              <button
                                type="button"
                                class={"btn btn-sm " + (isBusy() ? "btn-disabled" : "btn-primary")}
                                disabled={isBusy()}
                                onClick={() => void saveGraphRename().then((saved) => {
                                  if (saved) {
                                    setIsRenamePopoverOpen(false);
                                  }
                                })}
                              >
                                {isSavingRename() ? "Saving..." : "Save"}
                              </button>
                            </div>
                          </div>
                        </Popover.Content>
                      </Popover.Portal>
                    </>
                  )}
                </Popover>
              </Show>
              <Dialog.Close class="btn btn-sm btn-ghost" disabled={isBusy()}>Close</Dialog.Close>
            </div>
          </div>

          <div class="space-y-4 overflow-y-auto">
            <TraceControls
              traceOptions={traceOptions()}
              selectedTraceIndex={selectedTraceIndex()}
              traceNameDraft={traceNameDraft()}
              selectedTraceColor={selectedTraceColorValue()}
              colorOptions={TRACE_COLOR_OPTIONS}
              disableAddTrace={isBusy()}
              disableTraceSelect={isBusy() || traceOptions().length === 0}
              disableColorSelect={isBusy() || !selectedTraceType()}
              disableTraceNameInput={isBusy() || !selectedTrace()}
              disableRenameTrace={isBusy() || !selectedTrace()}
              disableRemoveTrace={isBusy() || !selectedTrace()}
              onAddTrace={addNewTrace}
              onSelectTrace={setSelectedTraceIndex}
              onChangeTraceName={setTraceNameDraft}
              onApplyColor={applyTraceColor}
              onRenameTrace={renameSelectedTrace}
              onRemoveTrace={() => void removeSelectedTrace()}
            />

            <Show
              when={selectedTrace()}
              fallback={
                <p class="rounded-lg border border-base-300 bg-base-200/40 p-3 text-sm text-base-content/70">
                  This graph has no traces to edit.
                </p>
              }
            >
              <Show
                when={selectedTraceType()}
                fallback={
                  <p class="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
                    Unsupported trace type "{unsupportedTraceType()}". Supported editors: pie, bar, scatter.
                  </p>
                }
              >
                <Switch>
                  <Match when={selectedTraceType() === "pie"}>
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
                  </Match>
                  <Match when={selectedTraceType() === "bar"}>
                    <CartesianTraceEditor
                      heading="Bar"
                      rowIndexes={cartesianRowIndexes()}
                      xValues={cartesianXValues()}
                      yValues={cartesianYValues()}
                      yRangeMin={selectedTraceYAxisRange()[0]}
                      yRangeMax={selectedTraceYAxisRange()[1]}
                      isBusy={isBusy()}
                      onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
                      onUpdateX={(rowIndex, rawValue) =>
                        updateSelectedTrace((trace) => updateCartesianX(trace, rowIndex, rawValue))
                      }
                      onUpdateY={(rowIndex, rawValue) =>
                        updateSelectedTrace((trace) => updateCartesianY(trace, rowIndex, rawValue))
                      }
                      onUpdateYRangeMin={(rawValue) => updateSelectedTraceYRange(0, rawValue)}
                      onUpdateYRangeMax={(rawValue) => updateSelectedTraceYRange(1, rawValue)}
                      onRemoveRow={(rowIndex) =>
                        updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex))
                      }
                    />
                  </Match>
                  <Match when={selectedTraceType() === "scatter"}>
                    <CartesianTraceEditor
                      heading="Scatter"
                      rowIndexes={cartesianRowIndexes()}
                      xValues={cartesianXValues()}
                      yValues={cartesianYValues()}
                      yRangeMin={selectedTraceYAxisRange()[0]}
                      yRangeMax={selectedTraceYAxisRange()[1]}
                      isBusy={isBusy()}
                      onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
                      onUpdateX={(rowIndex, rawValue) =>
                        updateSelectedTrace((trace) => updateCartesianX(trace, rowIndex, rawValue))
                      }
                      onUpdateY={(rowIndex, rawValue) =>
                        updateSelectedTrace((trace) => updateCartesianY(trace, rowIndex, rawValue))
                      }
                      onUpdateYRangeMin={(rawValue) => updateSelectedTraceYRange(0, rawValue)}
                      onUpdateYRangeMax={(rawValue) => updateSelectedTraceYRange(1, rawValue)}
                      onRemoveRow={(rowIndex) =>
                        updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex))
                      }
                    />
                  </Match>
                </Switch>
              </Show>
            </Show>
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
