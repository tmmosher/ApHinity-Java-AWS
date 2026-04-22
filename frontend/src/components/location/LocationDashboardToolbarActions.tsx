import {Show} from "solid-js";
import LocationWorkOrderActions from "./LocationWorkOrderActions";

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
  onUndo: () => void;
};

const toolbarActionButtonClass =
  "btn h-11 min-h-11 rounded-2xl px-4 text-sm font-medium shadow-sm transition duration-150 ease-out " +
  "motion-reduce:transform-none motion-reduce:transition-none hover:-translate-y-px active:translate-y-px active:scale-[0.98]";

export const LocationDashboardToolbarActions = (props: LocationDashboardToolbarActionsProps) => (
  <div class="flex flex-col gap-3 md:items-end">
    <Show when={props.canEditGraphs}>
      <div class="flex flex-wrap items-center gap-3">
        <LocationWorkOrderActions class={toolbarActionButtonClass}/>
        <button
          type="button"
          class={toolbarActionButtonClass + " " + (props.canCreateGraphs ? "btn-outline" : "btn-disabled")}
          disabled={!props.canCreateGraphs}
          title={props.createGraphDisabledReason}
          onClick={props.onAddGraph}
        >
          {props.isCreatingGraph ? "Creating..." : "Add Graph"}
        </button>
        <button
          type="button"
          class={toolbarActionButtonClass + " " + (props.hasPendingGraphChanges && !props.isGraphMutationBusy ? "btn-primary" : "btn-disabled")}
          disabled={!props.hasPendingGraphChanges || props.isGraphMutationBusy}
          onClick={props.onApply}
        >
          Apply
        </button>
        <button
          type="button"
          class={toolbarActionButtonClass + " " + (props.hasPendingGraphChanges && !props.isGraphMutationBusy ? "btn-outline" : "btn-disabled")}
          disabled={!props.hasPendingGraphChanges || props.isGraphMutationBusy}
          onClick={props.onUndo}
        >
          Undo
        </button>
      </div>
      <Show when={props.hasPendingGraphChanges}>
        <p class="text-right text-xs text-base-content/70">
          {props.pendingGraphMutationCount} pending graph mutation{props.pendingGraphMutationCount === 1 ? "" : "s"}
        </p>
      </Show>
    </Show>
  </div>
);

export default LocationDashboardToolbarActions;
