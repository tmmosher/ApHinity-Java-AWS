import {A} from "@solidjs/router";
import GraphCreateModal from "../../../../components/graph-editor/GraphCreateModal";
import GraphEditorModal from "../../../../components/graph-editor/GraphEditorModal";
import LocationDashboardLayoutModal from "../../../../components/location/LocationDashboardLayoutModal";
import {For, Show, createMemo, createResource} from "solid-js";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {canEditLocationGraphs} from "../../../../util/common/profileAccess";
import {useLocationDetail} from "../../../../context/LocationDetailContext";
import {createDashboardLocationResetGuard} from "../../../../util/location/locationView";
import {createLocationDashboardEditController} from "../../../../util/location/createLocationDashboardEditController";
import LocationDashboardToolbar from "../../../../components/location/LocationDashboardToolbar";
import LocationDashboardSection from "../../../../components/location/LocationDashboardSection";
import {loadPlotlyModule} from "../../../../components/common/Chart";

type LocationDashboardPanelProps = {
  locationId: string;
};

export const LocationDashboardPanel = (props: LocationDashboardPanelProps) => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {location, graphs, graphsError, refetchLocation, refetchGraphs} = useLocationDetail();
  const canEditGraphs = createMemo(() => canEditLocationGraphs(profileContext.profile()?.role));
  const shouldResetDashboardState = createDashboardLocationResetGuard(props.locationId);
  const dashboard = createLocationDashboardEditController({
    host,
    locationId: () => props.locationId,
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

  return (
    <div class="space-y-4">
      <LocationDashboardToolbar
        canEditGraphs={canEditGraphs()}
        canManageWorkOrderEmail={canEditGraphs()}
        canCreateGraphs={dashboard.canCreateGraphs()}
        isCreatingGraph={dashboard.isCreatingGraph()}
        hasPendingGraphChanges={dashboard.hasPendingDashboardChanges()}
        isGraphMutationBusy={dashboard.isGraphMutationBusy()}
        pendingGraphMutationCount={dashboard.pendingDashboardMutationCount()}
        updatedAtLabel={dashboard.updatedAtLabel()}
        createGraphDisabledReason={createGraphDisabledReason()}
        onAddGraph={dashboard.openCreateGraphModal}
        onApply={() => void dashboard.applyGraphChanges()}
        onEditLayout={dashboard.openLayoutEditor}
        onUndo={dashboard.undoLastDashboardEdit}
      />

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
                {(section) => (
                  <LocationDashboardSection
                    section={section}
                    graphs={sectionGraphs(section)}
                    missingGraphIds={missingGraphIds(section)}
                    canEditGraphs={canEditGraphs()}
                    isGraphMutationBusy={dashboard.isGraphMutationBusy()}
                    plotlyModule={plotlyModule}
                    onOpenGraphEditor={dashboard.openGraphEditor}
                  />
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
