import Popover from "corvu/popover";
import {Show, createSignal, type JSX} from "solid-js";
import {toast} from "solid-toast";
import LocationWorkOrderEmailPopover from "./LocationWorkOrderEmailPopover";
import LocationDashboardToolbarOverflowMenuAddGraphAction from "./LocationDashboardToolbarOverflowMenuAddGraphAction";
import LocationDashboardToolbarOverflowMenuEditLayoutAction from "./LocationDashboardToolbarOverflowMenuEditLayoutAction";
import LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction from "./LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction";
import LocationOverflowMenuIcon from "./LocationOverflowMenuIcon";
import {locationToolbarIconButtonClass} from "./locationToolbarStyles";
import {uploadLocationDashboardSpreadsheetById} from "../../util/graph/locationDetailApi";
import type {LocationDashboardSpreadsheetUploadResult} from "../../types/Types";

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
  apiHost: string;
  locationId: string;
  isCreatingGraph: boolean;
  isGraphMutationBusy: boolean;
  monthRange: number;
  createGraphDisabledReason?: string;
  onAddGraph: () => void;
  onEditLayout: () => void;
  onUploadSpreadsheetSuccess?: (result: LocationDashboardSpreadsheetUploadResult, file: File) => Promise<void> | void;
};

export const LocationDashboardToolbarOverflowMenu = (
  props: LocationDashboardToolbarOverflowMenuProps
) => {
  const [isUploadingSpreadsheet, setIsUploadingSpreadsheet] = createSignal(false);
  let spreadsheetUploadInputRef: HTMLInputElement | undefined;
  const spreadsheetUploadDisabledReason = "Dashboard spreadsheets can only be uploaded from All Data.";
  const isSpreadsheetUploadDisabled = () => props.monthRange > 0;

  const openSpreadsheetUploadDialog = () => {
    if (props.isGraphMutationBusy || isUploadingSpreadsheet() || isSpreadsheetUploadDisabled()) {
      return;
    }
    spreadsheetUploadInputRef?.click();
  };

  const handleSpreadsheetUploadChange: JSX.EventHandler<HTMLInputElement, Event> = async (event) => {
    const input = event.currentTarget;
    const file = input.files?.item(0);
    input.value = "";

    if (!file) {
      return;
    }

    if (props.isGraphMutationBusy || isUploadingSpreadsheet() || isSpreadsheetUploadDisabled()) {
      return;
    }

    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      toast.error("Select an .xlsx file");
      return;
    }

    setIsUploadingSpreadsheet(true);
    try {
      const uploadedGraphs = await uploadLocationDashboardSpreadsheetById(
        props.apiHost,
        props.locationId,
        file,
        false,
        props.monthRange
      );
      await props.onUploadSpreadsheetSuccess?.(uploadedGraphs, file);
      toast.success("Spreadsheet uploaded");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to upload dashboard spreadsheet");
    } finally {
      setIsUploadingSpreadsheet(false);
    }
  };

  return (
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
                <LocationDashboardToolbarOverflowMenuUploadSpreadsheetAction
                  isUploadingSpreadsheet={isUploadingSpreadsheet()}
                  isUploadDisabled={isSpreadsheetUploadDisabled()}
                  uploadDisabledReason={spreadsheetUploadDisabledReason}
                  onUploadSpreadsheet={openSpreadsheetUploadDialog}
                />
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
      <Show when={props.canEditGraphs}>
        <input
          ref={(element) => {
            spreadsheetUploadInputRef = element;
          }}
          type="file"
          class="hidden"
          accept=".xlsx"
          aria-label="Upload dashboard spreadsheet"
          data-dashboard-spreadsheet-upload-input=""
          disabled={props.isGraphMutationBusy || isUploadingSpreadsheet() || isSpreadsheetUploadDisabled()}
          onChange={handleSpreadsheetUploadChange}
        />
      </Show>
    </Show>
  );
};

export default LocationDashboardToolbarOverflowMenu;
