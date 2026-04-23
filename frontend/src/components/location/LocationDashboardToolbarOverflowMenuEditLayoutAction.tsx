import Popover from "corvu/popover";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarOverflowMenuEditLayoutActionProps = {
  isGraphMutationBusy: boolean;
  onEditLayout: () => void;
};

export const LocationDashboardToolbarOverflowMenuEditLayoutAction = (
  props: LocationDashboardToolbarOverflowMenuEditLayoutActionProps
) => (
  <Popover.Close
    type="button"
    class={locationToolbarActionButtonClass + " w-full justify-start " + (!props.isGraphMutationBusy ? "btn-outline" : "btn-disabled")}
    disabled={props.isGraphMutationBusy}
    onClick={() => {
      if (props.isGraphMutationBusy) {
        return;
      }
      props.onEditLayout();
    }}
  >
    Edit Layout
  </Popover.Close>
);

export default LocationDashboardToolbarOverflowMenuEditLayoutAction;
