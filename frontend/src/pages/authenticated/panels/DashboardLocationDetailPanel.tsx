import {A, useParams} from "@solidjs/router";
import PlotlyChart, {loadPlotlyModule} from "../../../components/Chart";
import type {PlotlyConfig, PlotlyData, PlotlyLayout} from "../../../components/Chart";
import GraphEditorModal from "../../../components/graph-editor/GraphEditorModal";
import {For, Show, Suspense, createEffect, createMemo, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {LocationGraph, LocationSectionLayout} from "../../../types/Types";
import {
  applyGraphPayloadEdit,
  buildLocationGraphUpdates,
  cloneLocationGraphs,
  undoGraphPayloadEdit,
  type EditableGraphPayload
} from "../../../util/graphEditor";
import {resolveGraphHeight} from "../../../util/graphTheme";
import {fetchLocationById, fetchLocationGraphsById, saveLocationGraphsById} from "../../../util/locationDetailApi";
import {canEditLocationGraphs} from "../../../util/profileAccess";

export const DashboardLocationDetailPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();

  const [location, {refetch: refetchLocation}] = createResource(
    () => params.locationId,
    (locationId) => fetchLocationById(host, locationId)
  );
  const [graphs, {refetch: refetchGraphs}] = createResource(
    () => params.locationId,
    (locationId) => fetchLocationGraphsById(host, locationId)
  );
  const [workingGraphs, setWorkingGraphs] = createSignal<LocationGraph[]>([]);
  const [locationUndoStack, setLocationUndoStack] = createSignal<LocationGraph[][]>([]);
  const [editingGraphId, setEditingGraphId] = createSignal<number | null>(null);
  const [isSavingGraphChanges, setIsSavingGraphChanges] = createSignal(false);
  const [locationSessionToken, setLocationSessionToken] = createSignal(0);

  const canEditGraphs = createMemo(() =>
    canEditLocationGraphs(profileContext.profile()?.role)
  );
  const hasPendingGraphChanges = createMemo(() => locationUndoStack().length > 0);

  createEffect(() => {
    const fetchedGraphs = graphs();
    if (!fetchedGraphs) {
      return;
    }
    setWorkingGraphs(cloneLocationGraphs(fetchedGraphs));
    setLocationUndoStack([]);
  });

  createEffect(() => {
    params.locationId;
    setEditingGraphId(null);
    setIsSavingGraphChanges(false);
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
      return sections.some((section) => section.graph_ids.some((graphId) => byId.has(graphId)));
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

  const graphLoadingFallback = (graphName: string) => (
    <div class="graph-loading-placeholder h-72 w-full" role="status" aria-live="polite" aria-label={`Loading graph ${graphName}`}>
      <div class="graph-loading-line graph-loading-line-wide" />
      <div class="graph-loading-line graph-loading-line-medium" />
      <div class="graph-loading-bars">
        <span class="graph-loading-bar graph-loading-bar-1" />
        <span class="graph-loading-bar graph-loading-bar-2" />
        <span class="graph-loading-bar graph-loading-bar-3" />
        <span class="graph-loading-bar graph-loading-bar-4" />
      </div>
    </div>
  );

  const closeGraphEditor = () => setEditingGraphId(null);

  const openGraphEditor = (graphId: number) => {
    if (isSavingGraphChanges() || !canEditGraphs()) {
      return;
    }
    setEditingGraphId(graphId);
  };

  const applyLocalGraphEdit = (graphId: number, payload: EditableGraphPayload) => {
    if (isSavingGraphChanges() || !canEditGraphs()) {
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

  const undoLastGraphEdit = () => {
    if (isSavingGraphChanges()) {
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
    if (isSavingGraphChanges() || !hasPendingGraphChanges() || !canEditGraphs()) {
      return;
    }

    const saveLocationId = params.locationId;
    const saveSessionToken = locationSessionToken();
    const graphUpdates = buildLocationGraphUpdates(workingGraphs());

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
    } catch {
      if (saveLocationId !== params.locationId || saveSessionToken !== locationSessionToken()) {
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
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Location</h1>
      </header>

      <Show when={!location.loading && !graphs.loading} fallback={<p class="text-base-content/70">Loading location dashboard...</p>}>
        <Show when={!location.error && !graphs.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load location dashboard.</p>
            <div class="flex gap-2">
              <button type="button" class="btn btn-outline" onClick={retryAll}>
                Retry
              </button>
              <A href="/dashboard/locations" class="btn btn-ghost">
                Back to locations
              </A>
            </div>
          </div>
        }>
          <div class="space-y-4">
            <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
              <h2 class="text-xl font-semibold">{location()?.name}</h2>
              <p class="mt-2 text-sm text-base-content/70">
                Last updated {updatedAtLabel()}
              </p>
              <Show when={canEditGraphs()}>
                <div class="mt-4 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    class={"btn btn-sm " + (hasPendingGraphChanges() && !isSavingGraphChanges() ? "btn-primary" : "btn-disabled")}
                    disabled={!hasPendingGraphChanges() || isSavingGraphChanges()}
                    onClick={() => void applyGraphChanges()}
                  >
                    Apply
                  </button>
                  <button
                    type="button"
                    class={"btn btn-sm " + (hasPendingGraphChanges() && !isSavingGraphChanges() ? "btn-outline" : "btn-disabled")}
                    disabled={!hasPendingGraphChanges() || isSavingGraphChanges()}
                    onClick={undoLastGraphEdit}
                  >
                    Undo
                  </button>
                  <Show when={hasPendingGraphChanges()}>
                    <span class="text-xs text-base-content/70">
                      {locationUndoStack().length} pending graph mutation{locationUndoStack().length === 1 ? "" : "s"}
                    </span>
                  </Show>
                </div>
              </Show>
            </section>

            <Show when={orderedSections().length > 0} fallback={
              <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
                <p class="text-base-content/70">No dashboard sections are configured for this location.</p>
              </section>
            }>
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
                                      class={"btn btn-xs " + (isSavingGraphChanges() ? "btn-disabled" : "btn-outline")}
                                      disabled={isSavingGraphChanges()}
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
                                  <Suspense fallback={graphLoadingFallback(graph.name)}>
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
          </div>
        </Show>
      </Show>

      <Show when={canEditGraphs()}>
        <GraphEditorModal
          isOpen={editingGraphId() !== null}
          graph={editingGraph()}
          canUndo={hasPendingGraphChanges() && !isSavingGraphChanges()}
          isSaving={isSavingGraphChanges()}
          onApply={applyLocalGraphEdit}
          onUndo={undoLastGraphEdit}
          onClose={closeGraphEditor}
        />
      </Show>
    </div>
  );
};
