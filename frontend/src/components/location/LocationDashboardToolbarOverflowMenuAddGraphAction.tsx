import Popover from "corvu/popover";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarOverflowMenuAddGraphActionProps = {
  canCreateGraphs: boolean;
  isCreatingGraph: boolean;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
};

export const LocationDashboardToolbarOverflowMenuAddGraphAction = (
  props: LocationDashboardToolbarOverflowMenuAddGraphActionProps
) => (
  <Popover.Close
    type="button"
    class={locationToolbarActionButtonClass + " w-full justify-start " + (props.canCreateGraphs ? "btn-outline" : "btn-disabled")}
    disabled={!props.canCreateGraphs}
    title={props.createGraphDisabledReason}
    onClick={() => {
      if (!props.canCreateGraphs) {
        return;
      }
      props.onAddGraph();
    }}
  >
    {props.isCreatingGraph ? "Creating..." : "Add New Graph"}
  </Popover.Close>
);

export default LocationDashboardToolbarOverflowMenuAddGraphAction;
