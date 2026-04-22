import Dialog from "corvu/dialog";
import {For, Show, createEffect, createMemo, createSignal, on} from "solid-js";
import type {LocationGraph, LocationSectionLayout, LocationSectionLayoutConfig} from "../../types/Types";
import {
  areLocationSectionLayoutsEqual,
  cloneLocationSectionLayout,
  findGraphLayoutPosition,
  findSectionLayoutIndex,
  moveGraphWithinLayout,
  moveSectionWithinLayout
} from "../../util/location/dashboardLayoutEdit";
import {createMapById} from "../../util/common/indexById";
import {getEditableGraphTitle} from "../../util/graph/graphEditor";

type DragState =
  | {
      kind: "section";
      sectionId: number;
    }
  | {
      kind: "graph";
      graphId: number;
    }
  | null;

type LocationDashboardLayoutModalProps = {
  isOpen: boolean;
  sectionLayout: LocationSectionLayoutConfig;
  graphs: LocationGraph[];
  onSave: (nextSectionLayout: LocationSectionLayoutConfig) => void;
  onClose: () => void;
};

const sectionBoxClass =
  "rounded-2xl border border-sky-300 bg-sky-100/90 p-4 shadow-sm transition duration-150 ease-out " +
  "dark:border-sky-700 dark:bg-sky-950/30";
const sectionDragShadowClass =
  " relative z-10 scale-[0.995] opacity-95 shadow-[0_24px_54px_-24px_rgba(2,132,199,0.9)] " +
  "ring-2 ring-sky-400/55 dark:ring-sky-300/45";

const graphBoxClass =
  "rounded-xl border border-emerald-300 bg-emerald-100/90 p-3 shadow-sm transition duration-150 ease-out " +
  "dark:border-emerald-700 dark:bg-emerald-950/30";
const graphDragShadowClass =
  " relative z-10 scale-[0.99] opacity-95 shadow-[0_20px_44px_-22px_rgba(16,185,129,0.95)] " +
  "ring-2 ring-emerald-400/55 dark:ring-emerald-300/45";

export const LocationDashboardLayoutModal = (props: LocationDashboardLayoutModalProps) => {
  let sectionListElement: HTMLDivElement | undefined;
  const [draftLayout, setDraftLayout] = createSignal<LocationSectionLayoutConfig>(
    cloneLocationSectionLayout(props.sectionLayout)
  );
  const [dragState, setDragState] = createSignal<DragState>(null);
  const [dragOriginLayout, setDragOriginLayout] = createSignal<LocationSectionLayoutConfig | null>(null);
  const [saveError, setSaveError] = createSignal("");

  const graphLookup = createMemo(() => createMapById(props.graphs));

  createEffect(on(
    () => [props.isOpen, props.sectionLayout] as const,
    ([isOpen, sectionLayout]) => {
      if (!isOpen) {
        setDragState(null);
        setDragOriginLayout(null);
        setSaveError("");
        return;
      }

      setDraftLayout(cloneLocationSectionLayout(sectionLayout));
      setDragState(null);
      setDragOriginLayout(null);
      setSaveError("");
    }
  ));

  const isDirty = createMemo(() =>
    !areLocationSectionLayoutsEqual(draftLayout(), props.sectionLayout)
  );

  const currentSections = createMemo(() => draftLayout().sections);

  const missingGraphIdsForSection = (section: LocationSectionLayout): number[] =>
    section.graph_ids.filter((graphId) => !graphLookup().has(graphId));

  const graphTitle = (graph: LocationGraph): string => {
    const title = getEditableGraphTitle(graph.layout ?? null).trim();
    return title.length > 0 ? title : "Untitled";
  };

  const close = () => {
    setSaveError("");
    props.onClose();
  };

  const save = () => {
    if (!isDirty()) {
      props.onClose();
      return;
    }

    try {
      props.onSave(cloneLocationSectionLayout(draftLayout()));
      setSaveError("");
      props.onClose();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Unable to stage layout changes.");
    }
  };

  const beginSectionDrag = (sectionId: number) => {
    setDragOriginLayout(cloneLocationSectionLayout(draftLayout()));
    setDragState({kind: "section", sectionId});
  };

  const beginGraphDrag = (graphId: number) => {
    setDragOriginLayout(cloneLocationSectionLayout(draftLayout()));
    setDragState({kind: "graph", graphId});
  };

  const cancelDragPreview = () => {
    const originLayout = dragOriginLayout();
    if (originLayout) {
      setDraftLayout(cloneLocationSectionLayout(originLayout));
    }

    setDragState(null);
    setDragOriginLayout(null);
  };

  const commitDragPreview = () => {
    setDragState(null);
    setDragOriginLayout(null);
  };

  const isDraggingSection = (sectionId: number): boolean => {
    const state = dragState();
    return state?.kind === "section" && state.sectionId === sectionId;
  };

  const isDraggingGraph = (graphId: number): boolean => {
    const state = dragState();
    return state?.kind === "graph" && state.graphId === graphId;
  };

  const isAfterVerticalMidpoint = (event: DragEvent & {currentTarget: HTMLElement}) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return event.clientY >= rect.top + rect.height / 2;
  };

  const isAfterGraphMidpoint = (event: DragEvent & {currentTarget: HTMLElement}) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const gridRect = event.currentTarget.parentElement?.getBoundingClientRect();
    const isSingleColumn = !gridRect || gridRect.width < rect.width * 1.5;

    if (isSingleColumn) {
      return event.clientY >= rect.top + rect.height / 2;
    }

    return event.clientX >= rect.left + rect.width / 2;
  };

  const moveDraggedSectionNear = (targetSectionId: number, insertAfter: boolean) => {
    const state = dragState();
    if (state?.kind !== "section") {
      return;
    }

    setDraftLayout((current) => {
      const fromIndex = findSectionLayoutIndex(current, state.sectionId);
      const targetIndex = findSectionLayoutIndex(current, targetSectionId);
      if (fromIndex < 0 || targetIndex < 0) {
        return current;
      }

      const next = moveSectionWithinLayout(current, fromIndex, targetIndex + (insertAfter ? 1 : 0));
      return areLocationSectionLayoutsEqual(current, next) ? current : next;
    });
  };

  const moveDraggedSectionToEnd = () => {
    const state = dragState();
    if (state?.kind !== "section") {
      return;
    }

    setDraftLayout((current) => {
      const fromIndex = findSectionLayoutIndex(current, state.sectionId);
      if (fromIndex < 0) {
        return current;
      }

      const next = moveSectionWithinLayout(current, fromIndex, current.sections.length);
      return areLocationSectionLayoutsEqual(current, next) ? current : next;
    });
  };

  const moveDraggedGraphTo = (
    targetSectionId: number,
    targetGraphIndex?: number
  ) => {
    const state = dragState();
    if (state?.kind !== "graph") {
      return;
    }

    setDraftLayout((current) => {
      const sourcePosition = findGraphLayoutPosition(current, state.graphId);
      const targetSectionIndex = findSectionLayoutIndex(current, targetSectionId);
      if (!sourcePosition || targetSectionIndex < 0) {
        return current;
      }

      const next = moveGraphWithinLayout(
        current,
        sourcePosition.sectionIndex,
        sourcePosition.graphIndex,
        targetSectionIndex,
        targetGraphIndex
      );
      return areLocationSectionLayoutsEqual(current, next) ? current : next;
    });
  };

  const moveDraggedGraphToLastSection = () => {
    const sections = currentSections();
    const lastSection = sections[sections.length - 1];
    if (lastSection) {
      moveDraggedGraphTo(lastSection.section_id);
    }
  };

  const isPastSectionListEnd = (event: DragEvent) => {
    const lastSectionElement = sectionListElement?.lastElementChild;
    if (!(lastSectionElement instanceof HTMLElement)) {
      return true;
    }

    return event.clientY >= lastSectionElement.getBoundingClientRect().bottom;
  };

  const moveSectionByStep = (sectionIndex: number, direction: -1 | 1) => {
    const targetIndex = direction < 0 ? sectionIndex - 1 : sectionIndex + 2;
    if (targetIndex < 0 || targetIndex > currentSections().length) {
      return;
    }

    setDraftLayout((current) => moveSectionWithinLayout(current, sectionIndex, targetIndex));
  };

  const moveGraphByStep = (sectionIndex: number, graphIndex: number, direction: -1 | 1) => {
    const section = currentSections()[sectionIndex];
    if (!section) {
      return;
    }

    const targetGraphIndex = direction < 0 ? graphIndex - 1 : graphIndex + 2;
    if (targetGraphIndex < 0 || targetGraphIndex > section.graph_ids.length) {
      return;
    }

    setDraftLayout((current) => moveGraphWithinLayout(
      current,
      sectionIndex,
      graphIndex,
      sectionIndex,
      targetGraphIndex
    ));
  };

  const handleSectionListDrop = () => {
    if (!dragState() || currentSections().length === 0) {
      return;
    }

    commitDragPreview();
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
      <Dialog.Portal>
        <Dialog.Overlay class="fixed inset-0 z-50 bg-black/50 data-closed:pointer-events-none" />
        <Dialog.Content
          aria-labelledby="dashboard-layout-modal-title"
          aria-describedby="dashboard-layout-modal-description"
          class="fixed inset-0 z-[60] m-auto flex h-[min(92vh,56rem)] w-[min(96vw,72rem)] flex-col overflow-hidden rounded-3xl border border-base-300 bg-base-100 shadow-2xl data-closed:pointer-events-none"
        >
          <div class="flex items-start justify-between gap-4 border-b border-base-300 px-5 py-4">
            <div class="space-y-1">
              <Dialog.Label id="dashboard-layout-modal-title" class="text-xl font-semibold tracking-tight">
                Reorder Dashboard Layout
              </Dialog.Label>
              <Dialog.Description id="dashboard-layout-modal-description" class="text-sm text-base-content/70">
                Drag the blue sections to reorder the dashboard. Drag the green graph cards to move graphs between sections or change their order within a section.
              </Dialog.Description>
            </div>
            <div class="flex items-center gap-2">
              <Dialog.Close class="btn btn-ghost btn-sm">
                Close
              </Dialog.Close>
            </div>
          </div>

          <div
            class="flex-1 overflow-y-auto px-5 py-4"
            onDragOver={(event) => {
              event.preventDefault();
              if (!isPastSectionListEnd(event)) {
                return;
              }

              const state = dragState();
              if (state?.kind === "section") {
                moveDraggedSectionToEnd();
              } else if (state?.kind === "graph") {
                moveDraggedGraphToLastSection();
              }
            }}
            onDrop={(event) => {
              event.preventDefault();
              handleSectionListDrop();
            }}
          >
            <div ref={sectionListElement} class="space-y-4">
              <For each={currentSections()}>
                {(section, sectionIndex) => (
                  <section
                    class={sectionBoxClass + (isDraggingSection(section.section_id) ? sectionDragShadowClass : "")}
                    data-drag-active={isDraggingSection(section.section_id) ? "true" : undefined}
                    onDragOver={(event) => {
                      event.preventDefault();
                      event.stopPropagation();
                      const state = dragState();
                      if (state?.kind === "section") {
                        moveDraggedSectionNear(section.section_id, isAfterVerticalMidpoint(event));
                      } else if (state?.kind === "graph") {
                        moveDraggedGraphTo(section.section_id);
                      }
                    }}
                    onDrop={(event) => {
                      event.preventDefault();
                      event.stopPropagation();
                      commitDragPreview();
                    }}
                  >
                    <div class="mb-4 flex items-start justify-between gap-3">
                      <button
                        type="button"
                        class="cursor-grab text-left active:cursor-grabbing"
                        aria-label={`Drag section ${section.section_id}`}
                        draggable="true"
                        onDragStart={(event) => {
                          const dataTransfer = event.dataTransfer;
                          if (dataTransfer) {
                            dataTransfer.effectAllowed = "move";
                            dataTransfer.setData("text/plain", `section:${section.section_id}`);
                          }
                          beginSectionDrag(section.section_id);
                        }}
                        onDragEnd={cancelDragPreview}
                      >
                        <div class="space-y-1">
                          <p class="text-sm font-semibold tracking-tight text-sky-950 dark:text-sky-50">
                            Section {section.section_id}
                          </p>
                          <p class="text-xs text-sky-950/70 dark:text-sky-50/70">
                            Order {sectionIndex() + 1} of {currentSections().length} - {section.graph_ids.length} graph{section.graph_ids.length === 1 ? "" : "s"}
                          </p>
                        </div>
                      </button>
                      <div class="flex flex-col items-end gap-1">
                        <p class="text-xs font-medium uppercase tracking-wide text-sky-950/70 dark:text-sky-50/70">
                          Drag section
                        </p>
                        <div class="flex items-center gap-1">
                          <button
                            type="button"
                            class="btn btn-xs btn-ghost"
                            aria-label={`Move section ${section.section_id} up`}
                            disabled={sectionIndex() === 0}
                            onClick={() => moveSectionByStep(sectionIndex(), -1)}
                          >
                            Up
                          </button>
                          <button
                            type="button"
                            class="btn btn-xs btn-ghost"
                            aria-label={`Move section ${section.section_id} down`}
                            disabled={sectionIndex() === currentSections().length - 1}
                            onClick={() => moveSectionByStep(sectionIndex(), 1)}
                          >
                            Down
                          </button>
                        </div>
                      </div>
                    </div>

                    <Show
                      when={section.graph_ids.length > 0}
                      fallback={
                        <div class="rounded-xl border border-dashed border-emerald-400/60 bg-white/40 px-4 py-8 text-sm text-sky-950/70 dark:border-emerald-700/60 dark:bg-sky-950/20 dark:text-sky-50/70">
                          Drop graphs here
                        </div>
                      }
                    >
                      <div class="grid gap-3 md:grid-cols-2">
                        <For each={section.graph_ids}>
                          {(graphId, graphIndex) => {
                            const graph = () => graphLookup().get(graphId);
                            return (
                              <div
                                class={
                                  graphBoxClass
                                  + " cursor-grab active:cursor-grabbing"
                                  + (isDraggingGraph(graphId) ? graphDragShadowClass : "")
                                }
                                data-drag-active={isDraggingGraph(graphId) ? "true" : undefined}
                                aria-label={`Drag graph ${graphId}`}
                                aria-roledescription="draggable graph card"
                                draggable="true"
                                onDragStart={(event) => {
                                  const dataTransfer = event.dataTransfer;
                                  if (dataTransfer) {
                                    dataTransfer.effectAllowed = "move";
                                    dataTransfer.setData("text/plain", `graph:${graphId}`);
                                  }
                                  beginGraphDrag(graphId);
                                }}
                                onDragEnd={cancelDragPreview}
                                onDragOver={(event) => {
                                  if (dragState()?.kind !== "graph") {
                                    return;
                                  }

                                  event.preventDefault();
                                  event.stopPropagation();
                                  moveDraggedGraphTo(
                                    section.section_id,
                                    graphIndex() + (isAfterGraphMidpoint(event) ? 1 : 0)
                                  );
                                }}
                                onDrop={(event) => {
                                  if (dragState()?.kind !== "graph") {
                                    return;
                                  }

                                  event.preventDefault();
                                  event.stopPropagation();
                                  commitDragPreview();
                                }}
                              >
                                <div class="flex items-start justify-between gap-2">
                                  <Show
                                    when={graph()}
                                    fallback={
                                      <div class="space-y-1">
                                        <p class="text-sm font-semibold text-emerald-950 dark:text-emerald-50">
                                          Missing graph {graphId}
                                        </p>
                                        <p class="text-xs text-emerald-950/70 dark:text-emerald-50/70">
                                          This graph is no longer available.
                                        </p>
                                      </div>
                                    }
                                  >
                                    {(graphValue) => (
                                      <div class="min-w-0 space-y-1">
                                        <p class="text-sm font-semibold text-emerald-950 dark:text-emerald-50">
                                          {graphValue().name}
                                        </p>
                                        <p class="text-xs text-emerald-950/70 dark:text-emerald-50/70">
                                          {graphTitle(graphValue())}
                                        </p>
                                      </div>
                                    )}
                                  </Show>

                                  <div class="flex shrink-0 flex-col gap-1">
                                    <button
                                      type="button"
                                      class="btn btn-xs btn-ghost"
                                      aria-label={`Move graph ${graphId} up`}
                                      disabled={graphIndex() === 0}
                                      onClick={() => moveGraphByStep(sectionIndex(), graphIndex(), -1)}
                                    >
                                      Up
                                    </button>
                                    <button
                                      type="button"
                                      class="btn btn-xs btn-ghost"
                                      aria-label={`Move graph ${graphId} down`}
                                      disabled={graphIndex() === section.graph_ids.length - 1}
                                      onClick={() => moveGraphByStep(sectionIndex(), graphIndex(), 1)}
                                    >
                                      Down
                                    </button>
                                  </div>
                                </div>
                              </div>
                            );
                          }}
                        </For>
                      </div>
                    </Show>

                    <Show when={missingGraphIdsForSection(section).length > 0}>
                      <p class="mt-3 text-xs font-medium text-amber-700 dark:text-amber-300">
                        Missing graph IDs: {missingGraphIdsForSection(section).join(", ")}
                      </p>
                    </Show>
                  </section>
                )}
              </For>
            </div>
          </div>

          <div class="border-t border-base-300 px-5 py-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div class="space-y-1">
                <p class="text-sm font-medium">
                  {isDirty() ? "Local ordering changes are staged." : "No ordering changes staged."}
                </p>
                <Show when={saveError()}>
                  <p class="text-sm text-error">{saveError()}</p>
                </Show>
              </div>

              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="btn btn-ghost"
                  onClick={close}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  class={"btn " + (isDirty() ? "btn-primary" : "btn-disabled")}
                  disabled={!isDirty()}
                  onClick={save}
                >
                  Save
                </button>
              </div>
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
};

export default LocationDashboardLayoutModal;
