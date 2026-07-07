import {Show} from "solid-js";
import LocationDashboardToolbarOverflowMenu from "./LocationDashboardToolbarOverflowMenu";
import LocationLocationAlertsButton from "./LocationLocationAlertsButton";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";
import type {LocationDashboardSpreadsheetUploadResult} from "../../types/Types";

type LocationDashboardToolbarActionsProps = {
  canEditGraphs: boolean;
  canCreateGraphs: boolean;
  canManageWorkOrderEmail: boolean;
  apiHost: string;
  locationId: string;
  isCreatingGraph: boolean;
  hasPendingGraphChanges: boolean;
  isGraphMutationBusy: boolean;
  monthRange: number;
  pendingGraphMutationCount: number;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
  onApply: () => void;
  onEditLayout: () => void;
  onUploadSpreadsheetSuccess?: (result: LocationDashboardSpreadsheetUploadResult, file: File) => Promise<void> | void;
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
        apiHost={props.apiHost}
        locationId={props.locationId}
        isCreatingGraph={props.isCreatingGraph}
        isGraphMutationBusy={props.isGraphMutationBusy}
        monthRange={props.monthRange}
        createGraphDisabledReason={props.createGraphDisabledReason}
        onAddGraph={props.onAddGraph}
        onEditLayout={props.onEditLayout}
        onUploadSpreadsheetSuccess={props.onUploadSpreadsheetSuccess}
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
