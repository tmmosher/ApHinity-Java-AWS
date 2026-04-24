import Popover from "corvu/popover";
import {createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {LocationSummary} from "../../types/Types";
import {
  locationToolbarActionButtonClass,
  locationToolbarPopoverClass
} from "./locationToolbarStyles";

type LocationOverviewCardThumbnailUploadPopoverProps = {
  location: LocationSummary;
  onUpload: (locationId: number, file: File) => Promise<boolean>;
};

const ACCEPTED_THUMBNAIL_TYPES = ".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp";

export const LocationOverviewCardThumbnailUploadPopover = (
  props: LocationOverviewCardThumbnailUploadPopoverProps
) => {
  const [selectedFile, setSelectedFile] = createSignal<File | null>(null);
  const [uploadError, setUploadError] = createSignal("");
  const [isUploading, setIsUploading] = createSignal(false);
  let fileInputRef: HTMLInputElement | undefined;

  const clearUploadPopover = () => {
    setSelectedFile(null);
    setUploadError("");
    if (fileInputRef != null) {
      fileInputRef.value = "";
    }
  };

  const saveThumbnail = async (): Promise<boolean> => {
    if (isUploading()) {
      return false;
    }

    const file = selectedFile();
    if (!file) {
      setUploadError("Select a JPG, PNG, or WEBP image");
      return false;
    }

    setUploadError("");
    setIsUploading(true);
    try {
      const didSave = await props.onUpload(props.location.id, file);
      if (!didSave) {
        setUploadError("Unable to update location thumbnail");
        return false;
      }
      toast.success("Location thumbnail updated");
      return true;
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : "Unable to update location thumbnail");
      return false;
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <Popover
      placement="right-start"
      onOpenChange={(open) => {
        if (open) {
          clearUploadPopover();
          return;
        }
        clearUploadPopover();
      }}
    >
      {(popover) => (
        <>
          <Popover.Trigger
            type="button"
            class={
              locationToolbarActionButtonClass +
              " w-full justify-start " +
              (isUploading() ? "btn-disabled" : "btn-outline")
            }
            disabled={isUploading()}
            aria-label={props.location.thumbnailAvailable ? "Replace thumbnail" : "Upload thumbnail"}
          >
            {props.location.thumbnailAvailable ? "Replace thumbnail" : "Upload thumbnail"}
          </Popover.Trigger>
          <Popover.Portal>
            <Popover.Content class={locationToolbarPopoverClass}>
              <div class="space-y-3">
                <div class="space-y-1">
                  <Popover.Label class="text-sm font-semibold">
                    {props.location.thumbnailAvailable ? "Replace thumbnail" : "Upload thumbnail"}
                  </Popover.Label>
                  <Popover.Description class="text-xs text-base-content/70">
                    Upload a JPG, PNG, or WEBP image. The image will be converted to WEBP before it is stored.
                  </Popover.Description>
                </div>
                <label class="form-control w-full">
                  <span class="label-text text-xs">Image file</span>
                  <input
                    ref={(element) => {
                      fileInputRef = element;
                    }}
                    type="file"
                    class="file-input file-input-bordered file-input-sm w-full"
                    accept={ACCEPTED_THUMBNAIL_TYPES}
                    disabled={isUploading()}
                    onChange={(event) => {
                      const file = event.currentTarget.files?.item(0) ?? null;
                      setSelectedFile(file);
                      if (uploadError()) {
                        setUploadError("");
                      }
                    }}
                  />
                </label>
                <Show when={selectedFile()}>
                  <p class="text-xs text-base-content/70">
                    Selected: {selectedFile()!.name}
                  </p>
                </Show>
                <Show when={uploadError()}>
                  <p class="text-xs text-error">{uploadError()}</p>
                </Show>
                <div class="flex items-center justify-end gap-2">
                  <Popover.Close class="btn btn-ghost btn-sm" disabled={isUploading()} onClick={clearUploadPopover}>
                    Cancel
                  </Popover.Close>
                  <button
                    type="button"
                    class={locationToolbarActionButtonClass + " " + (isUploading() ? "btn-disabled" : "btn-primary")}
                    disabled={isUploading() || !selectedFile()}
                    onClick={() => void saveThumbnail().then((didSave) => {
                      if (didSave) {
                        popover.setOpen(false);
                      }
                    })}
                  >
                    {isUploading() ? "Uploading..." : "Upload"}
                  </button>
                </div>
              </div>
            </Popover.Content>
          </Popover.Portal>
        </>
      )}
    </Popover>
  );
};

export default LocationOverviewCardThumbnailUploadPopover;
