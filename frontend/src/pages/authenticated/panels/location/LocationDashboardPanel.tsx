import GraphCreateModal from "../../../../components/graph-editor/GraphCreateModal";
import GraphEditorModal from "../../../../components/graph-editor/GraphEditorModal";
import LocationDashboardLayoutModal from "../../../../components/location/LocationDashboardLayoutModal";
import {For, Show, createEffect, createMemo, createResource, on} from "solid-js";
import {useApiHost} from "../../../../context/ApiHostContext";
import {useProfile} from "../../../../context/ProfileContext";
import {canEditLocationGraphs} from "../../../../util/common/profileAccess";
import {useLocationDetail} from "../../../../context/LocationDetailContext";
import type {DashboardTimeRange} from "../../../../util/location/dashboardTimeRange";
import LocationDashboardToolbar from "../../../../components/location/LocationDashboardToolbar";
import LocationDashboardSection from "../../../../components/location/LocationDashboardSection";
import LocationDashboardTimeRangeSelector from "../../../../components/location/LocationDashboardTimeRangeSelector";
import {loadPlotlyModule} from "../../../../components/common/Chart";
import type {LocationDashboardSpreadsheetUploadResult} from "../../../../types/Types";
import {isTabulatorGraph} from "../../../../util/graph/tabulatorGraph";
import {monthRangeForDashboardTimeRange} from "../../../../util/location/dashboardTimeRange";

type LocationDashboardPanelProps = {
  locationId: string;
};

export const LocationDashboardPanel = (props: LocationDashboardPanelProps) => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {
    graphs,
    graphsError,
    graphTimeRange,
    setGraphTimeRange,
    dashboardEdit: dashboard,
    serviceCalendarStaging
  } = useLocationDetail();
  const canEditGraphs = createMemo(() => canEditLocationGraphs(profileContext.profile()?.role));

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
  const selectedTimeRange = () => graphTimeRange() as DashboardTimeRange;
  type DashboardSection = ReturnType<typeof orderedSections>[number];
  type DashboardSectionGroup =
    | {type: "flow"; sections: DashboardSection[]}
    | {type: "full"; section: DashboardSection};

  createEffect(on(
    () => props.locationId,
    () => {
      setGraphTimeRange("threeMonths");
    }
  ));

  const displayedSectionGraphs = (section: ReturnType<typeof orderedSections>[number]) =>
    sectionGraphs(section);

  const sectionLayoutGroups = createMemo<DashboardSectionGroup[]>(() => {
    const groups: DashboardSectionGroup[] = [];
    let flowSections: DashboardSection[] = [];
    const flushFlowSections = () => {
      if (flowSections.length > 0) {
        groups.push({type: "flow", sections: flowSections});
        flowSections = [];
      }
    };

    for (const section of orderedSections()) {
      if (displayedSectionGraphs(section).some(isTabulatorGraph)) {
        flushFlowSections();
        groups.push({type: "full", section});
        continue;
      }
      flowSections.push(section);
    }
    flushFlowSections();
    return groups;
  });

  const flowSectionColumns = (sections: DashboardSection[]): [DashboardSection[], DashboardSection[]] => [
    sections.filter((_, index) => index % 2 === 0),
    sections.filter((_, index) => index % 2 === 1)
  ];

  const applySpreadsheetUploadResult = (result: LocationDashboardSpreadsheetUploadResult, file: File): void => {
    dashboard.applySpreadsheetUploadPreview(result.graphs, file);
    if (result.correctiveActions.length > 0) {
      serviceCalendarStaging.stageImportedRequests(result.correctiveActions, {isCorrectiveAction: true});
    }
  };

  const [plotlyModule] = createResource(
    () => {
      const sections = orderedSections();
      if (sections.length === 0) {
        return false;
      }
      return sections.some((section) => sectionGraphs(section).some((graph) => !isTabulatorGraph(graph)));
    },
    async (shouldLoad) => (shouldLoad ? loadPlotlyModule() : null)
  );

  return (
    <div class="space-y-4">
      <LocationDashboardToolbar
        canEditGraphs={canEditGraphs()}
        canManageWorkOrderEmail={canEditGraphs()}
        apiHost={host}
        locationId={props.locationId}
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
        onUploadSpreadsheetSuccess={applySpreadsheetUploadResult}
        onUndo={dashboard.undoLastDashboardEdit}
      />

      <section class="rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-5">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div class="space-y-1">
            <h3 class="text-sm flex items-start font-semibold uppercase tracking-tight text-base-content/70">
              Date Range
            </h3>
          </div>

          <div class="flex justify-center md:justify-end">
            <LocationDashboardTimeRangeSelector
              selectedRange={selectedTimeRange}
              onSelectRange={setGraphTimeRange}
            />
          </div>
        </div>
      </section>

      <Show when={graphsError()}>
        <section class="rounded-xl border border-warning/30 bg-warning/10 p-4 text-sm text-warning-content">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p>Some graph payloads could not be loaded. The dashboard layout is still available below.</p>
            <button type="button" class="btn btn-sm btn-outline" onClick={dashboard.retryAll}>
              Retry Graphs
            </button>
          </div>
        </section>
      </Show>

      <Show
        when={graphs() || graphsError()}
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
            <For each={sectionLayoutGroups()}>
              {(group) => group.type === "full" ? (
                <LocationDashboardSection
                  section={group.section}
                  graphs={displayedSectionGraphs(group.section)}
                  missingGraphIds={missingGraphIds(group.section)}
                  apiHost={host}
                  locationId={props.locationId}
                  monthRange={monthRangeForDashboardTimeRange(selectedTimeRange())}
                  canEditGraphs={canEditGraphs()}
                  isGraphMutationBusy={dashboard.isGraphMutationBusy()}
                  plotlyModule={plotlyModule}
                  onOpenGraphEditor={dashboard.openGraphEditor}
                />
              ) : (
                <div class="grid gap-4 xl:grid-cols-2">
                  <For each={flowSectionColumns(group.sections)}>
                    {(columnSections) => (
                      <div class="space-y-4">
                        <For each={columnSections}>
                          {(section) => (
                            <LocationDashboardSection
                              section={section}
                              graphs={displayedSectionGraphs(section)}
                              missingGraphIds={missingGraphIds(section)}
                              apiHost={host}
                              locationId={props.locationId}
                              monthRange={monthRangeForDashboardTimeRange(selectedTimeRange())}
                              canEditGraphs={canEditGraphs()}
                              isGraphMutationBusy={dashboard.isGraphMutationBusy()}
                              plotlyModule={plotlyModule}
                              flowItem
                              onOpenGraphEditor={dashboard.openGraphEditor}
                            />
                          )}
                        </For>
                      </div>
                    )}
                  </For>
                </div>
              )}
            </For>
          </div>
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
          graph={dashboard.editingGraphForTimeRange(selectedTimeRange())}
          canRenameGraph={canEditGraphs() && !dashboard.isGraphMutationBusy()}
          canDeleteGraph={canEditGraphs() && !dashboard.hasPendingGraphChanges() && !dashboard.isGraphMutationBusy()}
          canEditData={selectedTimeRange() === "allTime"}
          canUndo={dashboard.hasPendingGraphChanges() && !dashboard.isGraphMutationBusy()}
          isDeleting={dashboard.isDeletingGraph()}
          isSaving={dashboard.isSavingGraphChanges()}
          onApply={(graphId, payload, description) =>
            dashboard.applyLocalGraphEdit(graphId, payload, description, selectedTimeRange())
          }
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
