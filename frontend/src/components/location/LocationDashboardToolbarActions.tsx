import {Show} from "solid-js";
import LocationWorkOrderActions from "./LocationWorkOrderActions";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarActionsProps = {
  canEditGraphs: boolean;
  canCreateGraphs: boolean;
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

export const LocationDashboardToolbarActions = (props: LocationDashboardToolbarActionsProps) => (
  <div class="flex flex-col gap-3 md:items-end">
    <div class="flex flex-wrap items-center gap-3">
      <LocationWorkOrderActions class="flex flex-wrap items-center gap-3" />
      <Show when={props.canEditGraphs}>
        <button
          type="button"
          class={locationToolbarActionButtonClass + " " + (props.canCreateGraphs ? "btn-outline" : "btn-disabled")}
          disabled={!props.canCreateGraphs}
          title={props.createGraphDisabledReason}
          onClick={props.onAddGraph}
        >
          {props.isCreatingGraph ? "Creating..." : "Add Graph"}
        </button>
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
        <button
          type="button"
          class={locationToolbarActionButtonClass + " " + (!props.isGraphMutationBusy ? "btn-outline" : "btn-disabled")}
          disabled={props.isGraphMutationBusy}
          onClick={props.onEditLayout}
        >
          Edit Layout
        </button>
      </Show>
    </div>
    <Show when={props.canEditGraphs && props.hasPendingGraphChanges}>
      <p class="text-right text-xs text-base-content/70">
        {props.pendingGraphMutationCount} pending graph mutation{props.pendingGraphMutationCount === 1 ? "" : "s"}
      </p>
    </Show>
  </div>
);

export default LocationDashboardToolbarActions;
