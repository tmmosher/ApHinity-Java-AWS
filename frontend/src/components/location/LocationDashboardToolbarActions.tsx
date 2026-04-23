import {Show} from "solid-js";
import LocationDashboardToolbarOverflowMenu from "./LocationDashboardToolbarOverflowMenu";
import LocationLocationAlertsButton from "./LocationLocationAlertsButton";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarActionsProps = {
  canEditGraphs: boolean;
  canCreateGraphs: boolean;
  canManageWorkOrderEmail: boolean;
  isCreatingGraph: boolean;
  hasPendingGraphChanges: boolean;
  isGraphMutationBusy: boolean;
  pendingGraphMutationCount: number;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
  onApply: () => void;
  onEditLayout: () => void;
  onUndo: () => void;
};

export const LocationDashboardToolbarActions = (
  props: LocationDashboardToolbarActionsProps
) => (
  <div class="flex flex-col gap-3 md:items-end">
    <div class="flex flex-wrap items-center gap-3">
      <LocationLocationAlertsButton />
      <Show when={props.canEditGraphs}>
        <button
          type="button"
          class={locationToolbarActionButtonClass + " " + (props.hasPendingGraphChanges && !props.isGraphMutationBusy ? "btn-primary" : "btn-disabled")}
          disabled={!props.hasPendingGraphChanges || props.isGraphMutationBusy}
          onClick={props.onApply}
        >
          Apply
        </button>
        <button
          type="button"
          class={locationToolbarActionButtonClass + " " + (props.hasPendingGraphChanges && !props.isGraphMutationBusy ? "btn-outline" : "btn-disabled")}
          disabled={!props.hasPendingGraphChanges || props.isGraphMutationBusy}
          onClick={props.onUndo}
        >
          Undo
        </button>
      </Show>
      <LocationDashboardToolbarOverflowMenu
        canEditGraphs={props.canEditGraphs}
        canCreateGraphs={props.canCreateGraphs}
        canManageWorkOrderEmail={props.canManageWorkOrderEmail}
        isCreatingGraph={props.isCreatingGraph}
        isGraphMutationBusy={props.isGraphMutationBusy}
        createGraphDisabledReason={props.createGraphDisabledReason}
        onAddGraph={props.onAddGraph}
        onEditLayout={props.onEditLayout}
      />
    </div>
    <Show when={props.canEditGraphs && props.hasPendingGraphChanges}>
      <p class="text-right text-xs text-base-content/70">
        {props.pendingGraphMutationCount} pending graph mutation{props.pendingGraphMutationCount === 1 ? "" : "s"}
      </p>
    </Show>
  </div>
);

export default LocationDashboardToolbarActions;
