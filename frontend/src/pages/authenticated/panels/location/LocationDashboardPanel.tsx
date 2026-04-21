import {A, useParams} from "@solidjs/router";
import PlotlyChart, {loadPlotlyModule} from "../../../../components/Chart";
import GraphCreateModal from "../../../../components/graph-editor/GraphCreateModal";
import type {PlotlyConfig, PlotlyData, PlotlyLayout} from "../../../../components/Chart";
import GraphEditorModal from "../../../../components/graph-editor/GraphEditorModal";
import {For, Show, Suspense, createEffect, createMemo, createResource, createSignal, untrack} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {LocationGraph, LocationSectionLayout} from "../../../../types/Types";
import {
  applyGraphPayloadEdit,
  buildChangedLocationGraphUpdates,
  type GraphBaselineEntry,
  reconcileLocationGraphRefreshState,
  pruneDeletedLocationGraphState,
  undoGraphPayloadEdit,
  type EditableGraphPayload
} from "../../../../util/graph/graphEditor";
import {
  buildCreateLocationGraphRequest,
  buildPostCreateGraphUpdate,
  type GraphCreateModalRequest
} from "../../../../util/graph/graphCreate";
import {resolveGraphHeight} from "../../../../util/graph/graphTheme";
import {
  createLocationGraphById,
  deleteLocationGraphById,
  renameLocationGraphById,
  saveLocationGraphsById
} from "../../../../util/graph/locationDetailApi";
import {canEditLocationGraphs} from "../../../../util/common/profileAccess";
import {useLocationDetail} from "../../../../context/LocationDetailContext";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import GraphLoadingPlaceholder from "../../../../components/graph/GraphLoadingPlaceholder";

const toolbarActionButtonClass =
  "btn h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

export const LocationDashboardPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const {location, graphs, graphsError, refetchLocation, refetchGraphs} = useLocationDetail();
  const [workingGraphs, setWorkingGraphs] = createSignal<LocationGraph[]>([]);
  const [graphBaselineIndex, setGraphBaselineIndex] = createSignal<Map<number, GraphBaselineEntry>>(new Map());
  const [locationUndoStack, setLocationUndoStack] = createSignal<LocationGraph[][]>([]);
  const [editingGraphId, setEditingGraphId] = createSignal<number | null>(null);
  const [isCreateGraphModalOpen, setIsCreateGraphModalOpen] = createSignal(false);
  const [isCreatingGraph, setIsCreatingGraph] = createSignal(false);
  const [isDeletingGraph, setIsDeletingGraph] = createSignal(false);
  const [isSavingGraphChanges, setIsSavingGraphChanges] = createSignal(false);
  const [locationSessionToken, setLocationSessionToken] = createSignal(0);
  const shouldResetDashboardState = createDashboardLocationResetGuard(params.locationId);

  const canEditGraphs = createMemo(() =>
    canEditLocationGraphs(profileContext.profile()?.role)
  );
  const hasPendingGraphChanges = createMemo(() => locationUndoStack().length > 0);
  const isGraphMutationBusy = createMemo(() =>
    isSavingGraphChanges() || isCreatingGraph() || isDeletingGraph()
  );

  createEffect(() => {
    const fetchedGraphs = graphs();
    if (!fetchedGraphs) {
      return;
    }
    const refreshState = untrack(() => reconcileLocationGraphRefreshState(
      workingGraphs(),
      locationUndoStack(),
      graphBaselineIndex(),
      fetchedGraphs
    ));
    // Rebase the local graph state so a server refresh does not erase unsaved edits.
    setWorkingGraphs(refreshState.nextGraphs);
    setGraphBaselineIndex(refreshState.nextBaselineIndex);
    setLocationUndoStack(refreshState.nextUndoStack);
  });

  createEffect(() => {
    if (!shouldResetDashboardState(params.locationId)) {
      return;
    }
    setEditingGraphId(null);
    setIsCreateGraphModalOpen(false);
    setIsCreatingGraph(false);
    setIsDeletingGraph(false);
    setIsSavingGraphChanges(false);
    setWorkingGraphs([]);
    setGraphBaselineIndex(new Map());
    setLocationUndoStack([]);
    setLocationSessionToken((token) => token + 1);
  });

  const retryAll = () => {
    void refetchLocation();
    void refetchGraphs();
  };

  const graphById = createMemo(() => {
    const byId = new Map<number, LocationGraph>();
    for (const graph of workingGraphs()) {
      byId.set(graph.id, graph);
    }
    return byId;
  });

  const editingGraph = createMemo(() => {
    const graphId = editingGraphId();
    if (graphId === null) {
      return undefined;
    }
    return graphById().get(graphId);
  });

  createEffect(() => {
    const graphId = editingGraphId();
    if (graphId === null) {
      return;
    }
    if (!graphById().has(graphId)) {
      setEditingGraphId(null);
    }
  });

  const orderedSections = createMemo(() => {
    const currentLocation = location();
    if (!currentLocation) {
      return [] as LocationSectionLayout[];
    }

    return [...currentLocation.sectionLayout.sections].sort(
      (left, right) => left.section_id - right.section_id
    );
  });

  const sectionOptions = createMemo(() =>
    orderedSections().map((section) => ({
      id: section.section_id,
      label: `Section ${section.section_id} (${section.graph_ids.length} graph${section.graph_ids.length === 1 ? "" : "s"})`
    }))
  );

  const nextSectionId = createMemo(() => {
    const sections = orderedSections();
    if (sections.length === 0) {
      return 1;
    }
    return Math.max(...sections.map((section) => section.section_id)) + 1;
  });

  const canCreateGraphs = createMemo(() =>
    canEditGraphs()
    && !hasPendingGraphChanges()
    && !isGraphMutationBusy()
  );

  const createGraphDisabledReason = () => {
    if (hasPendingGraphChanges()) {
      return "Apply or undo your pending graph changes before creating a new graph.";
    }
    if (isGraphMutationBusy()) {
      return "Another graph action is already in progress.";
    }
    return undefined;
  };

  const sectionGraphs = (section: LocationSectionLayout): LocationGraph[] =>
    section.graph_ids
      .map((graphId) => graphById().get(graphId))
      .filter((graph): graph is LocationGraph => graph !== undefined);

  const missingGraphIds = (section: LocationSectionLayout): number[] =>
    section.graph_ids.filter((graphId) => !graphById().has(graphId));

  const [plotlyModule] = createResource(
    () => {
      const sections = orderedSections();
      if (sections.length === 0) {
        return false;
      }
      const byId = graphById();
      return sections.some((section) => section.graph_ids.some((graphId: number) => byId.has(graphId)));
    },
    async (shouldLoad) => (shouldLoad ? loadPlotlyModule() : null)
  );

  const updatedAtLabel = () => {
    const current = location();
    if (!current) {
      return "-";
    }
    return new Date(current.updatedAt).toLocaleString();
  };

  const closeGraphEditor = () => setEditingGraphId(null);
  const closeCreateGraphModal = () => setIsCreateGraphModalOpen(false);

  const openGraphEditor = (graphId: number) => {
    if (isGraphMutationBusy() || !canEditGraphs()) {
      return;
    }
    setEditingGraphId(graphId);
  };

  const openCreateGraphModal = () => {
    if (!canCreateGraphs()) {
      return;
    }
    setIsCreateGraphModalOpen(true);
  };

  const resolveGraphCreateFollowUpFailureMessage = (error: unknown): string => {
    if (error instanceof Error && error.message === "CSRF invalid") {
      return "Graph created, but the custom Y-axis title could not be saved because the security token expired. Reopen the graph and try again.";
    }
    if (error instanceof Error && error.message === "Security token rejected") {
      return "Graph created, but the custom Y-axis title could not be saved because security validation failed. Reopen the graph and try again.";
    }
    if (error instanceof Error && error.message === "Authentication required") {
      return "Graph created, but the custom Y-axis title could not be saved because your session expired. Sign in again and retry from the graph editor.";
    }
    if (error instanceof Error && error.message === "Insufficient permissions") {
      return "Graph created, but the custom Y-axis title could not be saved because you no longer have permission to edit graphs.";
    }
    if (error instanceof Error && error.message === "Graph update conflict") {
      return "Graph created, but the custom Y-axis title could not be saved due to a graph update conflict. Reopen the graph and try again.";
    }
    return "Graph created, but the custom Y-axis title could not be saved. Reopen the graph and try again.";
  };

  const applyLocalGraphEdit = (graphId: number, payload: EditableGraphPayload) => {
    if (isGraphMutationBusy() || !canEditGraphs()) {
      return;
    }
    const result = applyGraphPayloadEdit(
      workingGraphs(),
      locationUndoStack(),
      graphId,
      payload
    );
    if (!result.changed) {
      return;
    }
    setWorkingGraphs(result.nextGraphs);
    setLocationUndoStack(result.nextUndoStack);
  };

  const renameGraphFromModal = async (graphId: number, name: string): Promise<void> => {
    if (isGraphMutationBusy() || !canEditGraphs()) {
      throw new Error("Unable to rename graph.");
    }

    const result = await renameLocationGraphById(host, params.locationId, graphId, name);

    setWorkingGraphs((currentGraphs) =>
      currentGraphs.map((graph) =>
        graph.id === graphId
          ? {
              ...graph,
              name: result.name,
              updatedAt: result.updatedAt
            }
          : graph
      )
    );
    setLocationUndoStack((currentUndoStack) =>
      currentUndoStack.map((snapshot) =>
        snapshot.map((graph) =>
          graph.id === graphId
            ? {
                ...graph,
                name: result.name,
                updatedAt: result.updatedAt
              }
            : graph
        )
      )
    );
    setGraphBaselineIndex((currentIndex) => {
      const nextIndex = new Map(currentIndex);
      const existingEntry = nextIndex.get(graphId);
      if (existingEntry) {
        nextIndex.set(graphId, {
          ...existingEntry,
          expectedUpdatedAt: result.updatedAt
        });
      }
      return nextIndex;
    });

    try {
      await refetchLocation();
    } catch {
      toast.error("Graph renamed, but location details could not refresh. Please refresh the page.");
    }
  };

  const createGraphFromModal = async (request: GraphCreateModalRequest): Promise<void> => {
    if (isGraphMutationBusy() || hasPendingGraphChanges() || !canEditGraphs()) {
      throw new Error("Unable to create graph.");
    }

    const createLocationId = params.locationId;
    const createSessionToken = locationSessionToken();
    let createdGraph: LocationGraph | null = null;
    setIsCreatingGraph(true);

    try {
      createdGraph = await createLocationGraphById(
        host,
        createLocationId,
        buildCreateLocationGraphRequest(request)
      );

      if (createLocationId !== params.locationId || createSessionToken !== locationSessionToken()) {
        return;
      }

      const postCreateUpdate = buildPostCreateGraphUpdate(createdGraph, request);
      if (postCreateUpdate) {
        await saveLocationGraphsById(host, createLocationId, [postCreateUpdate]);
        if (createLocationId !== params.locationId || createSessionToken !== locationSessionToken()) {
          return;
        }
      }

      setIsCreateGraphModalOpen(false);
      toast.success("Graph created.");

      try {
        await Promise.all([refetchGraphs(), refetchLocation()]);
        if (createLocationId !== params.locationId || createSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (createLocationId === params.locationId && createSessionToken === locationSessionToken()) {
          toast.error("Graph created, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (createLocationId !== params.locationId || createSessionToken !== locationSessionToken()) {
        return;
      }
      if (createdGraph) {
        setIsCreateGraphModalOpen(false);
        toast.error(resolveGraphCreateFollowUpFailureMessage(error));
        try {
          await Promise.all([refetchGraphs(), refetchLocation()]);
        } catch {
          if (createLocationId === params.locationId && createSessionToken === locationSessionToken()) {
            toast.error("Graph created, but automatic refresh failed. Please refresh the page.");
          }
        }
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        throw new Error("Security token refresh failed. Please retry creating the graph.");
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        throw new Error("Security validation failed. Retrying usually succeeds.");
      }
      if (error instanceof Error && error.message === "Authentication required") {
        throw new Error("Session refresh failed. Please sign in again.");
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        throw new Error("You no longer have permission to create graphs.");
      }
      if (error instanceof Error && error.message === "Location section not found") {
        throw new Error("Selected section is no longer available. Please refresh and try again.");
      }

      throw error instanceof Error ? error : new Error("Unable to create graph.");
    } finally {
      if (createLocationId === params.locationId && createSessionToken === locationSessionToken()) {
        setIsCreatingGraph(false);
      }
    }
  };

  const deleteGraphFromModal = async (graphId: number): Promise<void> => {
    if (hasPendingGraphChanges()) {
      throw new Error("Apply or undo your pending graph changes before deleting a graph.");
    }
    if (isGraphMutationBusy() || !canEditGraphs()) {
      throw new Error("Unable to delete graph.");
    }

    const deleteLocationId = params.locationId;
    const deleteSessionToken = locationSessionToken();
    setIsDeletingGraph(true);

    try {
      await deleteLocationGraphById(host, deleteLocationId, graphId);

      if (deleteLocationId !== params.locationId || deleteSessionToken !== locationSessionToken()) {
        return;
      }

      const cleanupResult = pruneDeletedLocationGraphState(
        workingGraphs(),
        locationUndoStack(),
        graphBaselineIndex(),
        graphId
      );
      setWorkingGraphs(cleanupResult.nextGraphs);
      setLocationUndoStack(cleanupResult.nextUndoStack);
      setGraphBaselineIndex(cleanupResult.nextBaselineIndex);
      setEditingGraphId(null);
      toast.success("Graph deleted.");

      try {
        await Promise.all([refetchGraphs(), refetchLocation()]);
        if (deleteLocationId !== params.locationId || deleteSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (deleteLocationId === params.locationId && deleteSessionToken === locationSessionToken()) {
          toast.error("Graph deleted, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (deleteLocationId !== params.locationId || deleteSessionToken !== locationSessionToken()) {
        return;
      }

      if (error instanceof Error && error.message === "CSRF invalid") {
        throw new Error("Security token refresh failed. Please retry deleting the graph.");
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        throw new Error("Security validation failed. Retrying usually succeeds.");
      }
      if (error instanceof Error && error.message === "Authentication required") {
        throw new Error("Session refresh failed. Please sign in again.");
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        throw new Error("You no longer have permission to delete graphs.");
      }
      if (error instanceof Error && error.message === "Location graph not found") {
        throw new Error("Selected graph is no longer available. Please refresh and try again.");
      }

      throw error instanceof Error ? error : new Error("Unable to delete graph.");
    } finally {
      if (deleteLocationId === params.locationId && deleteSessionToken === locationSessionToken()) {
        setIsDeletingGraph(false);
      }
    }
  };

  const undoLastGraphEdit = () => {
    if (isGraphMutationBusy()) {
      return;
    }
    const result = undoGraphPayloadEdit(workingGraphs(), locationUndoStack());
    if (!result.undone) {
      return;
    }
    setWorkingGraphs(result.nextGraphs);
    setLocationUndoStack(result.nextUndoStack);
  };

  const applyGraphChanges = async () => {
    if (isGraphMutationBusy() || !hasPendingGraphChanges() || !canEditGraphs()) {
      return;
    }

    const saveLocationId = params.locationId;
    const saveSessionToken = locationSessionToken();
    const graphUpdates = buildChangedLocationGraphUpdates(workingGraphs(), graphBaselineIndex());
    if (graphUpdates.length === 0) {
      setLocationUndoStack([]);
      return;
    }

    setIsSavingGraphChanges(true);
    try {
      await saveLocationGraphsById(
        host,
        saveLocationId,
        graphUpdates
      );
      if (saveLocationId !== params.locationId || saveSessionToken !== locationSessionToken()) {
        return;
      }
      setLocationUndoStack([]);
      toast.success("Graph changes saved.");
      try {
        await Promise.all([refetchGraphs(), refetchLocation()]);
        if (saveLocationId !== params.locationId || saveSessionToken !== locationSessionToken()) {
          return;
        }
      } catch {
        if (saveLocationId === params.locationId && saveSessionToken === locationSessionToken()) {
          toast.error("Saved successfully, but automatic refresh failed. Please refresh the page.");
        }
      }
    } catch (error) {
      if (saveLocationId !== params.locationId || saveSessionToken !== locationSessionToken()) {
        return;
      }
      if (error instanceof Error && error.message === "Graph update conflict") {
        setLocationUndoStack([]);
        try {
          await refetchGraphs();
          if (saveLocationId !== params.locationId || saveSessionToken !== locationSessionToken()) {
            return;
          }
          toast.error("A graph was updated by another user. Latest data was reloaded.");
        } catch {
          if (saveLocationId === params.locationId && saveSessionToken === locationSessionToken()) {
            toast.error("A graph was updated by another user, and automatic refresh failed. Please refresh the page.");
          }
        }
        return;
      }
      if (error instanceof Error && error.message === "CSRF invalid") {
        toast.error("Security token refresh failed. Please retry Apply; your edits are still local.");
        return;
      }
      if (error instanceof Error && error.message === "Security token rejected") {
        toast.error("Security validation failed. Retrying Apply usually succeeds without losing local edits.");
        return;
      }
      if (error instanceof Error && error.message === "Authentication required") {
        toast.error("Session refresh failed. Please sign in again; your local edits are still on this page.");
        return;
      }
      if (error instanceof Error && error.message === "Insufficient permissions") {
        toast.error("You no longer have permission to edit these graphs.");
        return;
      }
      toast.error("Unable to save graph changes.");
    } finally {
      if (saveLocationId === params.locationId && saveSessionToken === locationSessionToken()) {
        setIsSavingGraphChanges(false);
      }
    }
  };

  return (
    <div class="space-y-4">
      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div class="space-y-1">
            <h2 class="text-xl flex items-center tracking-tight h-11 font-semibold">Dashboard</h2>
            <p class="text-xs text-base-content/70">
              Last updated {updatedAtLabel()}
            </p>
          </div>

          <div class="flex flex-col gap-3 md:items-end">
            <Show when={canEditGraphs()}>
              <div class="flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (canCreateGraphs() ? "btn-outline" : "btn-disabled")}
                  disabled={!canCreateGraphs()}
                  title={createGraphDisabledReason()}
                  onClick={openCreateGraphModal}
                >
                  {isCreatingGraph() ? "Creating..." : "Add Graph"}
                </button>
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (hasPendingGraphChanges() && !isGraphMutationBusy() ? "btn-primary" : "btn-disabled")}
                  disabled={!hasPendingGraphChanges() || isGraphMutationBusy()}
                  onClick={() => void applyGraphChanges()}
                >
                  Apply
                </button>
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (hasPendingGraphChanges() && !isGraphMutationBusy() ? "btn-outline" : "btn-disabled")}
                  disabled={!hasPendingGraphChanges() || isGraphMutationBusy()}
                  onClick={undoLastGraphEdit}
                >
                  Undo
                </button>
              </div>
              <Show when={hasPendingGraphChanges()}>
                <p class="text-right text-xs text-base-content/70">
                  {locationUndoStack().length} pending graph mutation{locationUndoStack().length === 1 ? "" : "s"}
                </p>
              </Show>
            </Show>
          </div>
        </div>
      </section>

      <Show
        when={!graphsError()}
        fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load location graphs.</p>
            <div class="flex gap-2">
              <button type="button" class="btn btn-outline" onClick={retryAll}>
                Retry
              </button>
              <A href="/dashboard/locations" class="btn btn-ghost">
                Back to locations
              </A>
            </div>
          </div>
        }
      >
        <Show
          when={graphs()}
          fallback={
            <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
              <p class="text-base-content/70">Loading location graphs...</p>
            </section>
          }
        >
          <Show
            when={orderedSections().length > 0}
            fallback={
              <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                <p class="text-base-content/70">No dashboard sections are configured for this location.</p>
              </section>
            }
          >
            <div class="space-y-4">
              <For each={orderedSections()}>
                {(section) => (
                  <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                    <Show when={sectionGraphs(section).length > 0} fallback={
                      <p class="text-sm text-base-content/70">
                        No available graph payloads for this section.
                      </p>
                    }>
                      <div class="grid gap-4 lg:grid-cols-2">
                        <For each={sectionGraphs(section)}>
                          {(graph) => (
                            <article class="rounded-lg border border-base-200 bg-base-200/40 p-3">
                              <div class="mb-2 flex items-start justify-between gap-2">
                                <h4 class="text-sm font-medium">{graph.name}</h4>
                                <Show when={canEditGraphs()}>
                                  <button
                                    type="button"
                                    class={"btn btn-xs " + (isGraphMutationBusy() ? "btn-disabled" : "btn-outline")}
                                    disabled={isGraphMutationBusy()}
                                    onClick={() => openGraphEditor(graph.id)}
                                  >
                                    Edit
                                  </button>
                                </Show>
                              </div>
                              <Show
                                when={!plotlyModule.error}
                                fallback={<p class="h-72 w-full rounded-lg border border-error/30 bg-error/10 p-4 text-sm text-error">Unable to load graph renderer.</p>}
                              >
                                <Suspense fallback={
                                  <div class="w-full overflow-hidden rounded-lg">
                                    <GraphLoadingPlaceholder graphName={graph.name} />
                                  </div>
                                }>
                                  <Show when={plotlyModule()}>
                                    <div class="w-full" style={{height: resolveGraphHeight(graph.style)}}>
                                      <PlotlyChart
                                        name={graph.name}
                                        data={graph.data as PlotlyData[]}
                                        layout={(graph.layout ?? undefined) as PlotlyLayout | undefined}
                                        config={(graph.config ?? undefined) as PlotlyConfig | undefined}
                                        style={graph.style ?? undefined}
                                        class="h-full w-full"
                                      />
                                    </div>
                                  </Show>
                                </Suspense>
                              </Show>
                            </article>
                          )}
                        </For>
                      </div>
                    </Show>

                    <Show when={missingGraphIds(section).length > 0}>
                      <p class="mt-3 text-xs text-warning">
                        Missing graph IDs: {missingGraphIds(section).join(", ")}
                      </p>
                    </Show>
                  </section>
                )}
              </For>
            </div>
          </Show>
        </Show>
      </Show>

      <Show when={canEditGraphs()}>
        <GraphCreateModal
          isOpen={isCreateGraphModalOpen()}
          isCreating={isCreatingGraph()}
          sectionOptions={sectionOptions()}
          nextSectionId={nextSectionId()}
          onCreate={createGraphFromModal}
          onClose={closeCreateGraphModal}
        />
        <GraphEditorModal
          isOpen={editingGraphId() !== null}
          graph={editingGraph()}
          canRenameGraph={canEditGraphs() && !isGraphMutationBusy()}
          canDeleteGraph={canEditGraphs() && !hasPendingGraphChanges() && !isGraphMutationBusy()}
          canUndo={hasPendingGraphChanges() && !isGraphMutationBusy()}
          isDeleting={isDeletingGraph()}
          isSaving={isSavingGraphChanges()}
          onApply={applyLocalGraphEdit}
          onDeleteGraph={deleteGraphFromModal}
          onRenameGraph={renameGraphFromModal}
          onUndo={undoLastGraphEdit}
          onClose={closeGraphEditor}
        />
      </Show>
    </div>
  );
};
