import Popover from "corvu/popover";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

type LocationDashboardToolbarOverflowMenuUploadSpreadsheetActionProps = {
  isUploadingSpreadsheet: boolean;
  isUploadDisabled: boolean;
  uploadDisabledReason?: string;
  onUploadSpreadsheet: () => void;
};

export const LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction = (
  props: LocationDashboardToolbarOverflowMenuUploadSpreadsheetActionProps
) => (
  <Popover.Close
    type="button"
    class={locationToolbarActionButtonClass + " w-full justify-start " + (props.isUploadingSpreadsheet || props.isUploadDisabled ? "btn-disabled" : "btn-outline")}
    disabled={props.isUploadingSpreadsheet || props.isUploadDisabled}
    title={props.isUploadDisabled ? props.uploadDisabledReason : undefined}
    onClick={() => {
      if (props.isUploadingSpreadsheet || props.isUploadDisabled) {
        return;
      }
      props.onUploadSpreadsheet();
    }}
  >
    {props.isUploadingSpreadsheet ? "Uploading..." : "Upload Spreadsheet"}
  </Popover.Close>
);

export default LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction;
