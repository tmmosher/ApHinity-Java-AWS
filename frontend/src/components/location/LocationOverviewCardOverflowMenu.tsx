import Popover from "corvu/popover";
import {Show} from "solid-js";
import {LocationSummary} from "../../types/Types";
import LocationOverviewCardRenamePopover from "./LocationOverviewCardRenamePopover";
import LocationOverviewCardThumbnailUploadPopover from "./LocationOverviewCardThumbnailUploadPopover";

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
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <circle cx="5" cy="12" r="1.5" />
    <circle cx="12" cy="12" r="1.5" />
    <circle cx="19" cy="12" r="1.5" />
  </svg>
);

type LocationOverviewCardOverflowMenuProps = {
  location: LocationSummary;
  canEditLocations: boolean;
  onRename: (locationId: number, nextName: string) => Promise<boolean>;
  onUploadThumbnail: (locationId: number, file: File) => Promise<boolean>;
};

export const LocationOverviewCardOverflowMenu = (props: LocationOverviewCardOverflowMenuProps) => (
  <Show when={props.canEditLocations}>
    <Popover {...overflowMenuProps}>
      <Popover.Trigger
        type="button"
        class="btn btn-circle btn-sm border border-base-300 bg-base-100/90 text-base-content/70 shadow-sm backdrop-blur"
        aria-label="More location actions"
      >
        <OverflowMenuIcon />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content class="z-[80] w-[min(92vw,18rem)] rounded-2xl border border-base-300 bg-base-100 p-2 shadow-xl">
          <div class="flex flex-col gap-2">
            <LocationOverviewCardRenamePopover location={props.location} onRename={props.onRename} />
            <LocationOverviewCardThumbnailUploadPopover
              location={props.location}
              onUpload={props.onUploadThumbnail}
            />
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover>
  </Show>
);

export default LocationOverviewCardOverflowMenu;
