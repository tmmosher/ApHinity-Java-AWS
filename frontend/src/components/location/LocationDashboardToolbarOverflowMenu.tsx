import Popover from "corvu/popover";
import {Show} from "solid-js";
import LocationWorkOrderEmailPopover from "./LocationWorkOrderEmailPopover";
import LocationDashboardToolbarOverflowMenuAddGraphAction from "./LocationDashboardToolbarOverflowMenuAddGraphAction";
import LocationDashboardToolbarOverflowMenuEditLayoutAction from "./LocationDashboardToolbarOverflowMenuEditLayoutAction";
import {locationToolbarIconButtonClass} from "./locationToolbarStyles";

const overflowMenuProps = {
  placement: "bottom-end" as const,
  trapFocus: false,
  restoreFocus: false,
  closeOnOutsideFocus: false
};

const OverflowMenuIcon = () => (
  <svg
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="lucide lucide-ellipsis-vertical-icon lucide-ellipsis-vertical"
  >
    <circle cx="12" cy="12" r="1" />
    <circle cx="12" cy="5" r="1" />
    <circle cx="12" cy="19" r="1" />
  </svg>
);

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
        <OverflowMenuIcon />
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
