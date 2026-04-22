import {LocationDashboardToolbarActions} from "./LocationDashboardToolbarActions";

type LocationDashboardToolbarProps = {
  canEditGraphs: boolean;
  canCreateGraphs: boolean;
  isCreatingGraph: boolean;
  hasPendingGraphChanges: boolean;
  isGraphMutationBusy: boolean;
  pendingGraphMutationCount: number;
  updatedAtLabel: string;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
  onApply: () => void;
  onEditLayout: () => void;
  onUndo: () => void;
};

export const LocationDashboardToolbar = (props: LocationDashboardToolbarProps) => (
  <section class="rounded-2xl border border-base-300 bg-base-100/70 p-4 shadow-sm md:p-6">
    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div class="space-y-1">
        <h2 class="flex h-11 items-center text-xl font-semibold tracking-tight">Dashboard</h2>
        <p class="text-xs text-base-content/70">
          Last updated {props.updatedAtLabel}
        </p>
      </div>

      <LocationDashboardToolbarActions
        canEditGraphs={props.canEditGraphs}
        canCreateGraphs={props.canCreateGraphs}
        isCreatingGraph={props.isCreatingGraph}
        hasPendingGraphChanges={props.hasPendingGraphChanges}
        isGraphMutationBusy={props.isGraphMutationBusy}
        pendingGraphMutationCount={props.pendingGraphMutationCount}
        createGraphDisabledReason={props.createGraphDisabledReason}
        onAddGraph={props.onAddGraph}
        onApply={props.onApply}
        onEditLayout={props.onEditLayout}
        onUndo={props.onUndo}
      />
    </div>
  </section>
);

export default LocationDashboardToolbar;
