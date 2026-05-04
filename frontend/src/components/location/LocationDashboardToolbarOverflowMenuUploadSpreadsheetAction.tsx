import Popover from "corvu/popover";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarOverflowMenuUploadSpreadsheetActionProps = {
  isUploadingSpreadsheet: boolean;
  onUploadSpreadsheet: () => void;
};

export const LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction = (
  props: LocationDashboardToolbarOverflowMenuUploadSpreadsheetActionProps
) => (
  <Popover.Close
    type="button"
    class={locationToolbarActionButtonClass + " w-full justify-start " + (props.isUploadingSpreadsheet ? "btn-disabled" : "btn-outline")}
    disabled={props.isUploadingSpreadsheet}
    onClick={() => {
      if (props.isUploadingSpreadsheet) {
        return;
      }
      props.onUploadSpreadsheet();
    }}
  >
    {props.isUploadingSpreadsheet ? "Uploading..." : "Upload Spreadsheet"}
  </Popover.Close>
);

export default LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction;
