import Popover from "corvu/popover";
import {Show} from "solid-js";
import LocationWorkOrderEmailPopover from "./LocationWorkOrderEmailPopover";
import LocationDashboardToolbarOverflowMenuAddGraphAction from "./LocationDashboardToolbarOverflowMenuAddGraphAction";
import LocationDashboardToolbarOverflowMenuEditLayoutAction from "./LocationDashboardToolbarOverflowMenuEditLayoutAction";
import LocationOverflowMenuIcon from "./LocationOverflowMenuIcon";
import {locationToolbarIconButtonClass} from "./locationToolbarStyles";

const overflowMenuProps = {
  placement: "bottom-end" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

type LocationDashboardToolbarOverflowMenuProps = {
  canEditGraphs: boolean;
  canCreateGraphs: boolean;
  canManageWorkOrderEmail: boolean;
  isCreatingGraph: boolean;
  isGraphMutationBusy: boolean;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
  onEditLayout: () => void;
};

export const LocationDashboardToolbarOverflowMenu = (
  props: LocationDashboardToolbarOverflowMenuProps
) => (
  <Show when={props.canEditGraphs || props.canManageWorkOrderEmail}>
    <Popover {...overflowMenuProps}>
      <Popover.Trigger
        type="button"
        class={locationToolbarIconButtonClass + " btn-outline"}
        aria-label="More actions"
      >
        <LocationOverflowMenuIcon />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content class="z-[80] w-[min(92vw,18rem)] rounded-2xl border border-base-300 bg-base-100 p-2 shadow-xl">
          <div class="flex flex-col gap-2">
            <LocationWorkOrderEmailPopover />
            <Show when={props.canEditGraphs}>
              <LocationDashboardToolbarOverflowMenuAddGraphAction
                canCreateGraphs={props.canCreateGraphs}
                isCreatingGraph={props.isCreatingGraph}
                createGraphDisabledReason={props.createGraphDisabledReason}
                onAddGraph={props.onAddGraph}
              />
              <LocationDashboardToolbarOverflowMenuEditLayoutAction
                isGraphMutationBusy={props.isGraphMutationBusy}
                onEditLayout={props.onEditLayout}
              />
            </Show>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  </Show>
);

export default LocationDashboardToolbarOverflowMenu;
