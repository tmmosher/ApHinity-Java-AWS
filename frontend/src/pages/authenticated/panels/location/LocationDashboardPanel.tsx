import {A, useParams} from "@solidjs/router";
import PlotlyChart, {loadPlotlyModule} from "../../../../components/Chart";
import GraphCreateModal from "../../../../components/graph-editor/GraphCreateModal";
import type {PlotlyConfig, PlotlyData, PlotlyLayout} from "../../../../components/Chart";
import GraphEditorModal from "../../../../components/graph-editor/GraphEditorModal";
import LocationDashboardLayoutModal from "../../../../components/location/LocationDashboardLayoutModal";
import {For, Show, Suspense, createMemo, createResource} from "solid-js";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {canEditLocationGraphs} from "../../../../util/common/profileAccess";
import {useLocationDetail} from "../../../../context/LocationDetailContext";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import GraphLoadingPlaceholder from "../../../../components/graph/GraphLoadingPlaceholder";
import {resolveGraphHeight} from "../../../../util/graph/graphTheme";
import {createLocationDashboardEditController} from "../../../../util/location/createLocationDashboardEditController";

const toolbarActionButtonClass =
  "btn h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

export const LocationDashboardPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const params = useParams<{ locationId: string }>();
  const {location, graphs, graphsError, refetchLocation, refetchGraphs} = useLocationDetail();
  const canEditGraphs = createMemo(() => canEditLocationGraphs(profileContext.profile()?.role));
  const shouldResetDashboardState = createDashboardLocationResetGuard(params.locationId);
  const dashboard = createLocationDashboardEditController({
    host,
    locationId: () => params.locationId,
    location,
    graphs,
    refetchLocation,
    refetchGraphs,
    canEditGraphs,
    shouldResetDashboardState
  });

  const createGraphDisabledReason = () => {
    if (dashboard.hasPendingDashboardChanges()) {
      return "Apply or undo your pending dashboard changes before creating a new graph.";
    }
    if (dashboard.isGraphMutationBusy()) {
      return "Another graph action is already in progress.";
    }
    return undefined;
  };

  const orderedSections = dashboard.orderedSections;
  const sectionGraphs = dashboard.sectionGraphs;
  const missingGraphIds = dashboard.missingGraphIds;

  const [plotlyModule] = createResource(
    () => {
      const sections = orderedSections();
      if (sections.length === 0) {
        return false;
      }
      return sections.some((section) => sectionGraphs(section).length > 0);
    },
    async (shouldLoad) => (shouldLoad ? loadPlotlyModule() : null)
  );

  const updatedAtLabel = () => dashboard.updatedAtLabel();

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
                  class={toolbarActionButtonClass + " " + (dashboard.canCreateGraphs() ? "btn-outline" : "btn-disabled")}
                  disabled={!dashboard.canCreateGraphs()}
                  title={createGraphDisabledReason()}
                  onClick={dashboard.openCreateGraphModal}
                >
                  {dashboard.isCreatingGraph() ? "Creating..." : "Add Graph"}
                </button>
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (dashboard.hasPendingDashboardChanges() && !dashboard.isGraphMutationBusy() ? "btn-primary" : "btn-disabled")}
                  disabled={!dashboard.hasPendingDashboardChanges() || dashboard.isGraphMutationBusy()}
                  onClick={() => void dashboard.applyGraphChanges()}
                >
                  Apply
                </button>
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (dashboard.hasPendingDashboardChanges() && !dashboard.isGraphMutationBusy() ? "btn-outline" : "btn-disabled")}
                  disabled={!dashboard.hasPendingDashboardChanges() || dashboard.isGraphMutationBusy()}
                  onClick={dashboard.undoLastDashboardEdit}
                >
                  Undo
                </button>
                <button
                  type="button"
                  class={toolbarActionButtonClass + " " + (!dashboard.isGraphMutationBusy() && canEditGraphs() ? "btn-outline" : "btn-disabled")}
                  disabled={dashboard.isGraphMutationBusy() || !canEditGraphs()}
                  onClick={dashboard.openLayoutEditor}
                >
                  Edit Layout
                </button>
              </div>
              <Show when={dashboard.hasPendingDashboardChanges()}>
                <p class="text-right text-xs text-base-content/70">
                  {dashboard.pendingDashboardMutationCount()} pending dashboard mutation{dashboard.pendingDashboardMutationCount() === 1 ? "" : "s"}
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
              <button type="button" class="btn btn-outline" onClick={dashboard.retryAll}>
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
                {(section, sectionIndex) => (
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
                                    class={"btn btn-xs " + (dashboard.isGraphMutationBusy() ? "btn-disabled" : "btn-outline")}
                                    disabled={dashboard.isGraphMutationBusy()}
                                    onClick={() => dashboard.openGraphEditor(graph.id)}
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
          isOpen={dashboard.isCreateGraphModalOpen()}
          isCreating={dashboard.isCreatingGraph()}
          sectionOptions={dashboard.sectionOptions()}
          nextSectionId={dashboard.nextSectionId()}
          onCreate={dashboard.createGraphFromModal}
          onClose={dashboard.closeCreateGraphModal}
        />
        <GraphEditorModal
          isOpen={dashboard.editingGraphId() !== null}
          graph={dashboard.editingGraph()}
          canRenameGraph={canEditGraphs() && !dashboard.isGraphMutationBusy()}
          canDeleteGraph={canEditGraphs() && !dashboard.hasPendingGraphChanges() && !dashboard.isGraphMutationBusy()}
          canUndo={dashboard.hasPendingGraphChanges() && !dashboard.isGraphMutationBusy()}
          isDeleting={dashboard.isDeletingGraph()}
          isSaving={dashboard.isSavingGraphChanges()}
          onApply={dashboard.applyLocalGraphEdit}
          onDeleteGraph={dashboard.deleteGraphFromModal}
          onRenameGraph={dashboard.renameGraphFromModal}
          onUndo={dashboard.undoLastDashboardEdit}
          onClose={dashboard.closeGraphEditor}
        />
        <LocationDashboardLayoutModal
          isOpen={dashboard.isLayoutEditorOpen()}
          sectionLayout={dashboard.workingSectionLayout()}
          graphs={dashboard.workingGraphs()}
          onSave={dashboard.applyLocalSectionLayoutEdit}
          onClose={dashboard.closeLayoutEditor}
        />
      </Show>
    </div>
  );
};

export default LocationDashboardPanel;
