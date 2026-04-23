import Dialog from "corvu/dialog";
import Popover from "corvu/popover";
import {batch, Match, Show, Switch, createEffect, createMemo, createSignal, on} from "solid-js";
import {loadPlotlyModule} from "../common/Chart";
import CartesianTraceEditor from "./CartesianTraceEditor";
import IndicatorTraceEditor from "./IndicatorTraceEditor";
import PieTraceEditor from "./PieTraceEditor";
import TraceControls from "./TraceControls";
import type {LocationGraph} from "../../types/Types";
import {
  type EditableGraphPayload,
  createEditableGraphPayload,
  getEditableGraphTitle,
  updateEditableGraphTitle
} from "../../util/graph/graphEditor";
import {
  addCartesianRow,
  addPieRow,
  buildTraceLabel,
  createTrace,
  getTraceArray,
  getPieRowColor,
  getTraceColor,
  getTraceType,
  getTraceYAxisRange,
  isRecord,
  removeCartesianRow,
  removePieRow,
  removeTraceWithPlotly,
  renameTrace,
  setPieRowColor,
  setTraceColor,
  parseNumericInput,
  updateTraceYAxisRange,
  updateCartesianX,
  updateCartesianY,
  updatePieLabel,
  updatePieValue,
  updateIndicatorValue as updateIndicatorTraceValue
} from "../../util/graph/graphTraceEditor";
import {
  INDICATOR_VALUE_MAX,
  INDICATOR_VALUE_MIN,
  isIncompleteNumericInput,
  TRACE_COLOR_OPTIONS,
  parseIndicatorValueInput
} from "../../util/graph/graphTemplateFactory";

type GraphEditorModalProps = {
  isOpen: boolean;
  graph: LocationGraph | undefined;
  canRenameGraph: boolean;
  canDeleteGraph: boolean;
  canUndo: boolean;
  isDeleting: boolean;
  isSaving: boolean;
  onApply: (graphId: number, payload: EditableGraphPayload) => void;
  onDeleteGraph: (graphId: number) => Promise<void>;
  onRenameGraph: (graphId: number, name: string) => Promise<void>;
  onUndo: () => void;
  onClose: () => void;
};

const TRACE_COLOR_VALUES = new Set(Object.values(TRACE_COLOR_OPTIONS));
const EMPTY_EDITABLE_GRAPH_PAYLOAD: EditableGraphPayload = {
  data: [],
  layout: null,
  config: null,
  style: null
};

export const GraphEditorModal = (props: GraphEditorModalProps) => {
  const [editablePayload, setEditablePayload] = createSignal<EditableGraphPayload>(
    props.graph ? createEditableGraphPayload(props.graph) : EMPTY_EDITABLE_GRAPH_PAYLOAD
  );
  const [selectedTraceIndex, setSelectedTraceIndex] = createSignal(0);
  const [traceNameDraft, setTraceNameDraft] = createSignal("");
  const [graphNameDraft, setGraphNameDraft] = createSignal(props.graph?.name ?? "");
  const [operationError, setOperationError] = createSignal("");
  const [renameError, setRenameError] = createSignal("");
  const [deleteError, setDeleteError] = createSignal("");
  const [isRemovingTrace, setIsRemovingTrace] = createSignal(false);
  const [isSavingRename, setIsSavingRename] = createSignal(false);
  const [isRenamePopoverOpen, setIsRenamePopoverOpen] = createSignal(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = createSignal(false);
  const [pieValueDrafts, setPieValueDrafts] = createSignal<Record<number, string>>({});
  const [cartesianXDrafts, setCartesianXDrafts] = createSignal<Record<number, string>>({});
  const [cartesianYDrafts, setCartesianYDrafts] = createSignal<Record<number, string>>({});
  const [yRangeMinDraft, setYRangeMinDraft] = createSignal<string | undefined>(undefined);
  const [yRangeMaxDraft, setYRangeMaxDraft] = createSignal<string | undefined>(undefined);
  const [indicatorValueDraft, setIndicatorValueDraft] = createSignal<string | undefined>(undefined);
  // Once the editor is closed or the graph disappears, render the empty state
  // immediately instead of showing stale trace data for one more pass.
  const visibleEditablePayload = () =>
    props.isOpen && props.graph !== undefined ? editablePayload() : EMPTY_EDITABLE_GRAPH_PAYLOAD;

  const isBusy = () => props.isSaving || props.isDeleting || isRemovingTrace() || isSavingRename();

  const clearTraceDrafts = () => {
    setPieValueDrafts({});
    setCartesianXDrafts({});
    setCartesianYDrafts({});
    setYRangeMinDraft(undefined);
    setYRangeMaxDraft(undefined);
    setIndicatorValueDraft(undefined);
  };

  const clearIndexedDraft = (
    setter: (value: Record<number, string> | ((current: Record<number, string>) => Record<number, string>)) => void,
    rowIndex: number
  ) => {
    setter((current) => {
      if (!(rowIndex in current)) {
        return current;
      }

      const nextDrafts = {...current};
      delete nextDrafts[rowIndex];
      return nextDrafts;
    });
  };

  const shiftIndexedDrafts = (drafts: Record<number, string>, removedIndex: number): Record<number, string> => {
    const nextDrafts: Record<number, string> = {};
    for (const [key, value] of Object.entries(drafts)) {
      const rowIndex = Number(key);
      if (!Number.isInteger(rowIndex)) {
        continue;
      }
      if (rowIndex < removedIndex) {
        nextDrafts[rowIndex] = value;
      } else if (rowIndex > removedIndex) {
        nextDrafts[rowIndex - 1] = value;
      }
    }
    return nextDrafts;
  };

  const resetEditorState = () => {
    batch(() => {
      setEditablePayload(EMPTY_EDITABLE_GRAPH_PAYLOAD);
      setSelectedTraceIndex(0);
      setTraceNameDraft("");
      setGraphNameDraft("");
      setOperationError("");
      setRenameError("");
      setDeleteError("");
      setIsRemovingTrace(false);
      setIsSavingRename(false);
      setIsRenamePopoverOpen(false);
      setIsDeleteConfirmOpen(false);
      clearTraceDrafts();
    });
  };

  createEffect(on(() => [props.isOpen, props.graph?.id] as const, ([isOpen, graphId]) => {
    if (!isOpen || graphId === undefined || props.graph === undefined) {
      resetEditorState();
      return;
    }

    const graph = props.graph;
    batch(() => {
      setEditablePayload(createEditableGraphPayload(graph));
      setGraphNameDraft(graph.name);
      setSelectedTraceIndex(0);
      setTraceNameDraft("");
      setOperationError("");
      setRenameError("");
      setDeleteError("");
      setIsRenamePopoverOpen(false);
      setIsDeleteConfirmOpen(false);
      clearTraceDrafts();
    });
  }));

  createEffect(() => {
    const count = visibleEditablePayload().data.length;
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
    visibleEditablePayload().data.map((entry, index) => {
      const trace = isRecord(entry) ? entry : {};
      return {
        index,
        label: buildTraceLabel(trace, index)
      };
    })
  );

  const isSingleTraceGraph = createMemo(() => {
    const firstTrace = visibleEditablePayload().data[0];
    if (!isRecord(firstTrace)) {
      return false;
    }

    const traceType = getTraceType(firstTrace);
    return traceType === "pie" || traceType === "indicator";
  });

  const selectedTrace = createMemo(() => {
    const trace = visibleEditablePayload().data[selectedTraceIndex()];
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

  const pieRowColors = createMemo(() => {
    const trace = selectedTrace();
    if (!trace) {
      return [] as string[];
    }
    return pieRowIndexes().map((rowIndex) => getPieRowColor(trace, rowIndex));
  });

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

  const visibleEditableGraphPayloadLayout = () => visibleEditablePayload().layout ?? null;

  const selectedTraceYAxisRange = createMemo<[unknown, unknown]>(() => {
    const trace = selectedTrace();
    if (!trace) {
      return ["", ""];
    }
    return getTraceYAxisRange(visibleEditableGraphPayloadLayout(), trace);
  });

  const graphTitleDraft = createMemo(() => getEditableGraphTitle(visibleEditableGraphPayloadLayout()));

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

  const updatePieValueDraft = (rowIndex: number, rawValue: string) => {
    if (parseNumericInput(rawValue) === null) {
      batch(() => {
        setPieValueDrafts((current) =>
          current[rowIndex] === rawValue ? current : {
            ...current,
            [rowIndex]: rawValue
          }
        );
        setOperationError("");
      });
      return;
    }

    batch(() => {
      clearIndexedDraft(setPieValueDrafts, rowIndex);
      updateSelectedTrace((trace) => updatePieValue(trace, rowIndex, rawValue));
    });
  };

  const updateIndicatorValueDraft = (rawValue: string) => {
    const parsedValue = parseIndicatorValueInput(rawValue);
    if (parsedValue === null) {
      batch(() => {
        setIndicatorValueDraft((current) => current === rawValue ? current : rawValue);
        setOperationError(
          rawValue.trim().length === 0 || isIncompleteNumericInput(rawValue)
            ? ""
            : `Indicator value must be between ${INDICATOR_VALUE_MIN} and ${INDICATOR_VALUE_MAX}.`
        );
      });
      return;
    }

    batch(() => {
      setIndicatorValueDraft(undefined);
      updateSelectedTrace((trace) => updateIndicatorTraceValue(trace, rawValue));
    });
  };

  const hasInvalidIndicatorDraft = () =>
    indicatorValueDraft() !== undefined && parseIndicatorValueInput(indicatorValueDraft() ?? "") === null;

  const updateCartesianXValue = (rowIndex: number, rawValue: string) => {
    const trace = selectedTrace();
    if (!trace) {
      return;
    }

    const currentValue = getTraceArray(trace, "x")[rowIndex];
    const shouldStageDraft =
      typeof currentValue === "number" && parseNumericInput(rawValue) === null && isIncompleteNumericInput(rawValue);

    if (shouldStageDraft) {
      batch(() => {
        setCartesianXDrafts((current) =>
          current[rowIndex] === rawValue ? current : {
            ...current,
            [rowIndex]: rawValue
          }
        );
        setOperationError("");
      });
      return;
    }

    batch(() => {
      clearIndexedDraft(setCartesianXDrafts, rowIndex);
      updateSelectedTrace((currentTrace) => updateCartesianX(currentTrace, rowIndex, rawValue));
    });
  };

  const updateCartesianYValue = (rowIndex: number, rawValue: string) => {
    if (parseNumericInput(rawValue) === null) {
      batch(() => {
        setCartesianYDrafts((current) =>
          current[rowIndex] === rawValue ? current : {
            ...current,
            [rowIndex]: rawValue
          }
        );
        setOperationError("");
      });
      return;
    }

    batch(() => {
      clearIndexedDraft(setCartesianYDrafts, rowIndex);
      updateSelectedTrace((trace) => updateCartesianY(trace, rowIndex, rawValue));
    });
  };

  const updateSelectedTraceYRange = (boundIndex: 0 | 1, rawValue: string) => {
    const isValidRangeInput = rawValue.length === 0 || parseNumericInput(rawValue) !== null;
    if (!isValidRangeInput) {
      batch(() => {
        if (boundIndex === 0) {
          setYRangeMinDraft(rawValue);
        } else {
          setYRangeMaxDraft(rawValue);
        }
        setOperationError("");
      });
      return;
    }

    batch(() => {
      if (boundIndex === 0) {
        setYRangeMinDraft(undefined);
      } else {
        setYRangeMaxDraft(undefined);
      }
      setEditablePayload((current) => {
        const existingTrace = current.data[selectedTraceIndex()];
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
    });
  };

  const applyTraceColor = (colorHex: string) => {
    const traceType = selectedTraceType();
    if (!traceType) {
      return;
    }

    updateSelectedTrace((trace) => setTraceColor(trace, traceType, colorHex));
  };

  const updateGraphTitle = (rawTitle: string) => {
    setEditablePayload((current) => ({
      ...current,
      layout: updateEditableGraphTitle(current.layout ?? null, rawTitle)
    }));
    setOperationError("");
  };

  const addNewTrace = () => {
    if (isBusy()) {
      return;
    }

    let nextTraceIndex = 0;
    let nextTraceName = "";

    batch(() => {
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
      clearTraceDrafts();
      setOperationError("");
    });
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

      batch(() => {
        setEditablePayload((payload) => ({
          ...payload,
          data: nextData
        }));
        clearTraceDrafts();
      });
    } catch (error) {
      setOperationError(error instanceof Error ? error.message : "Unable to remove trace.");
    } finally {
      setIsRemovingTrace(false);
    }
  };

  const close = () => {
    if (isBusy()) {
      return;
    }
    resetEditorState();
    props.onClose();
  };

  const applyEdit = () => {
    if (isBusy() || operationError() || hasInvalidIndicatorDraft()) {
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

  const selectTrace = (nextIndex: number) => {
    if (!Number.isInteger(nextIndex) || nextIndex < 0) {
      return;
    }

    batch(() => {
      setSelectedTraceIndex(nextIndex);
      clearTraceDrafts();
    });
  };

  const closeDeleteConfirm = () => {
    if (props.isDeleting) {
      return;
    }
    setDeleteError("");
    setIsDeleteConfirmOpen(false);
  };

  const openDeleteConfirm = () => {
    if (isBusy() || !props.canDeleteGraph || !props.graph) {
      return;
    }
    setDeleteError("");
    setIsDeleteConfirmOpen(true);
  };

  const deleteGraph = async (): Promise<void> => {
    const graph = props.graph;
    if (!graph || isBusy()) {
      return;
    }

    setDeleteError("");

    try {
      await props.onDeleteGraph(graph.id);
      resetEditorState();
      props.onClose();
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : "Unable to delete graph.");
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
            <section class="rounded-lg border border-base-300 bg-base-200/30 p-3">
              <label class="form-control w-full">
                <span class="label-text text-sm font-medium">Graph title</span>
                <span class="label-text-alt text-xs text-base-content/70">
                  This updates the chart title inside the graph layout.
                </span>
                <input
                  type="text"
                  class="input input-bordered input-sm mt-2 w-full"
                  value={graphTitleDraft()}
                  disabled={isBusy()}
                  placeholder="Optional graph title"
                  onInput={(event) => updateGraphTitle(event.currentTarget.value)}
                />
              </label>
            </section>

            <Show when={!isSingleTraceGraph()}>
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
                onSelectTrace={selectTrace}
                onChangeTraceName={setTraceNameDraft}
                onApplyColor={applyTraceColor}
                onRenameTrace={renameSelectedTrace}
                onRemoveTrace={() => void removeSelectedTrace()}
              />
            </Show>

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
                    Unsupported trace type "{unsupportedTraceType()}". Supported editors: pie, indicator, bar, scatter.
                  </p>
                }
              >
                <Switch>
                  <Match when={selectedTraceType() === "pie"}>
                    <PieTraceEditor
                      rowIndexes={pieRowIndexes()}
                      labels={pieLabels()}
                      values={pieValues()}
                      valueDrafts={pieValueDrafts()}
                      rowColors={pieRowColors()}
                      colorOptions={TRACE_COLOR_OPTIONS}
                      isBusy={isBusy()}
                      onAddRow={() => updateSelectedTrace((trace) => addPieRow(trace))}
                      onUpdateColor={(rowIndex, colorHex) =>
                        updateSelectedTrace((trace) => setPieRowColor(trace, rowIndex, colorHex))
                      }
                      onUpdateLabel={(rowIndex, rawValue) =>
                        updateSelectedTrace((trace) => updatePieLabel(trace, rowIndex, rawValue))
                      }
                      onUpdateValue={updatePieValueDraft}
                      onRemoveRow={(rowIndex) =>
                        batch(() => {
                          setPieValueDrafts((current) => shiftIndexedDrafts(current, rowIndex));
                          updateSelectedTrace((trace) => removePieRow(trace, rowIndex));
                        })
                      }
                    />
                  </Match>
                  <Match when={selectedTraceType() === "indicator"}>
                    <IndicatorTraceEditor
                      value={selectedTrace()?.value}
                      valueDraft={indicatorValueDraft()}
                      color={selectedTraceColorValue()}
                      colorOptions={TRACE_COLOR_OPTIONS}
                      isBusy={isBusy()}
                      onUpdateValue={updateIndicatorValueDraft}
                      onUpdateColor={(colorHex) =>
                        updateSelectedTrace((trace) => setTraceColor(trace, "indicator", colorHex))
                      }
                    />
                  </Match>
                  <Match when={selectedTraceType() === "bar"}>
                    <CartesianTraceEditor
                      heading="Bar"
                      rowIndexes={cartesianRowIndexes()}
                      xValues={cartesianXValues()}
                      yValues={cartesianYValues()}
                      xDrafts={cartesianXDrafts()}
                      yDrafts={cartesianYDrafts()}
                      yRangeMin={selectedTraceYAxisRange()[0]}
                      yRangeMax={selectedTraceYAxisRange()[1]}
                      yRangeMinDraft={yRangeMinDraft()}
                      yRangeMaxDraft={yRangeMaxDraft()}
                      isBusy={isBusy()}
                      onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
                      onUpdateX={(rowIndex, rawValue) =>
                        updateCartesianXValue(rowIndex, rawValue)
                      }
                      onUpdateY={(rowIndex, rawValue) =>
                        updateCartesianYValue(rowIndex, rawValue)
                      }
                      onUpdateYRangeMin={(rawValue) => updateSelectedTraceYRange(0, rawValue)}
                      onUpdateYRangeMax={(rawValue) => updateSelectedTraceYRange(1, rawValue)}
                      onRemoveRow={(rowIndex) =>
                        batch(() => {
                          setCartesianXDrafts((current) => shiftIndexedDrafts(current, rowIndex));
                          setCartesianYDrafts((current) => shiftIndexedDrafts(current, rowIndex));
                          updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex));
                        })
                      }
                    />
                  </Match>
                  <Match when={selectedTraceType() === "scatter"}>
                    <CartesianTraceEditor
                      heading="Scatter"
                      rowIndexes={cartesianRowIndexes()}
                      xValues={cartesianXValues()}
                      yValues={cartesianYValues()}
                      xDrafts={cartesianXDrafts()}
                      yDrafts={cartesianYDrafts()}
                      yRangeMin={selectedTraceYAxisRange()[0]}
                      yRangeMax={selectedTraceYAxisRange()[1]}
                      yRangeMinDraft={yRangeMinDraft()}
                      yRangeMaxDraft={yRangeMaxDraft()}
                      isBusy={isBusy()}
                      onAddRow={() => updateSelectedTrace((trace) => addCartesianRow(trace))}
                      onUpdateX={(rowIndex, rawValue) =>
                        updateCartesianXValue(rowIndex, rawValue)
                      }
                      onUpdateY={(rowIndex, rawValue) =>
                        updateCartesianYValue(rowIndex, rawValue)
                      }
                      onUpdateYRangeMin={(rawValue) => updateSelectedTraceYRange(0, rawValue)}
                      onUpdateYRangeMax={(rawValue) => updateSelectedTraceYRange(1, rawValue)}
                      onRemoveRow={(rowIndex) =>
                        batch(() => {
                          setCartesianXDrafts((current) => shiftIndexedDrafts(current, rowIndex));
                          setCartesianYDrafts((current) => shiftIndexedDrafts(current, rowIndex));
                          updateSelectedTrace((trace) => removeCartesianRow(trace, rowIndex));
                        })
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

          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center">
              <Show when={props.graph}>
                <button
                  type="button"
                  class={"btn btn-sm " + (props.canDeleteGraph && !isBusy() ? "btn-ghost text-error hover:bg-error/10" : "btn-disabled")}
                  disabled={!props.canDeleteGraph || isBusy()}
                  onClick={openDeleteConfirm}
                >
                  Delete Graph
                </button>
              </Show>
            </div>
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
                class={"btn btn-sm " + (isBusy() || operationError() || hasInvalidIndicatorDraft() ? "btn-disabled" : "btn-primary")}
                disabled={isBusy() || (!!operationError()) || hasInvalidIndicatorDraft()}
                onClick={applyEdit}
              >
                {props.isSaving ? "Saving..." : isRemovingTrace() ? "Updating..." : "Apply"}
              </button>
              <Dialog.Close class="btn btn-sm" disabled={isBusy()}>Close</Dialog.Close>
            </div>
          </div>
          {/* Keep the delete confirm inside the editor dialog subtree.
              A nested Dialog instance was closing as soon as delete started. */}
          <Show when={isDeleteConfirmOpen() || props.isDeleting}>
            <div
              class="fixed inset-0 z-[70] bg-black/55"
              aria-hidden="true"
              onClick={closeDeleteConfirm}
            />
            <div
              class="fixed inset-0 z-[80] m-auto flex h-fit w-[min(92vw,24rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-4 shadow-2xl md:p-5"
              role="dialog"
              aria-modal="true"
              aria-labelledby="delete-graph-title"
              aria-describedby="delete-graph-description"
              onClick={(event) => event.stopPropagation()}
            >
              <div class="space-y-1">
                <h2 id="delete-graph-title" class="text-lg font-semibold">Delete Graph</h2>
                <p id="delete-graph-description" class="text-sm text-base-content/70">
                  Delete {props.graph?.name ?? "this graph"} immediately from the dashboard.
                </p>
              </div>

              <p class="text-sm text-base-content/80">
                This sends the delete request to the server right away and cannot be undone. Use with caution.
              </p>

              <Show when={deleteError()}>
                <p class="text-sm text-error">{deleteError()}</p>
              </Show>

              <div class="flex flex-wrap items-center justify-end gap-2">
                <button
                  type="button"
                  class="btn btn-sm btn-ghost"
                  disabled={props.isDeleting}
                  onClick={closeDeleteConfirm}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  class={"btn btn-sm " + (props.isDeleting ? "btn-disabled" : "btn-error")}
                  disabled={props.isDeleting}
                  onClick={() => void deleteGraph()}
                >
                  {props.isDeleting ? "Deleting..." : "Delete"}
                </button>
              </div>
            </div>
          </Show>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default GraphEditorModal;
