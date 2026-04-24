import Popover from "corvu/popover";
import {createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {LocationSummary} from "../../types/Types";
import {
  locationToolbarActionButtonClass,
  locationToolbarPopoverClass
} from "./locationToolbarStyles";

type LocationOverviewCardRenamePopoverProps = {
  location: LocationSummary;
  onRename: (locationId: number, nextName: string) => Promise<boolean>;
};

export const LocationOverviewCardRenamePopover = (props: LocationOverviewCardRenamePopoverProps) => {
  const [renameDraft, setRenameDraft] = createSignal(props.location.name);
  const [renameError, setRenameError] = createSignal("");
  const [isRenaming, setIsRenaming] = createSignal(false);

  const clearRenamePopover = () => {
    setRenameError("");
    setRenameDraft(props.location.name);
  };

  const saveRename = async (): Promise<boolean> => {
    if (isRenaming()) {
      return false;
    }

    const nextName = renameDraft().trim();
    if (!nextName || nextName === props.location.name) {
      setRenameError("");
      return true;
    }

    setRenameError("");
    setIsRenaming(true);
    try {
      const didSave = await props.onRename(props.location.id, nextName);
      if (!didSave) {
        setRenameError("Unable to update location name");
        return false;
      }
      toast.success("Location updated");
      return true;
    } catch (error) {
      setRenameError(error instanceof Error ? error.message : "Unable to update location name");
      return false;
    } finally {
      setIsRenaming(false);
    }
  };

  return (
    <Popover
      placement="right-start"
      onOpenChange={(open) => {
        if (open) {
          setRenameDraft(props.location.name);
          setRenameError("");
          return;
        }
        clearRenamePopover();
      }}
    >
      {(popover) => (
        <>
          <Popover.Trigger
            type="button"
            class={
              locationToolbarActionButtonClass +
              " w-full justify-start " +
              (isRenaming() ? "btn-disabled" : "btn-outline")
            }
            disabled={isRenaming()}
            aria-label="Rename location"
          >
            Rename location
          </Popover.Trigger>
          <Popover.Portal>
            <Popover.Content class={locationToolbarPopoverClass}>
              <div class="space-y-3">
                <div class="space-y-1">
                  <Popover.Label class="text-sm font-semibold">
                    Rename location
                  </Popover.Label>
                  <Popover.Description class="text-xs text-base-content/70">
                    Update the name shown on this card and in the dashboard.
                  </Popover.Description>
                </div>
                <label class="form-control w-full">
                  <span class="label-text text-xs">Location name</span>
                  <input
                    type="text"
                    class="input input-bordered input-sm w-full"
                    value={renameDraft()}
                    maxlength={128}
                    disabled={isRenaming()}
                    onInput={(event) => {
                      setRenameDraft(event.currentTarget.value);
                      if (renameError()) {
                        setRenameError("");
                      }
                    }}
                  />
                </label>
                <Show when={renameError()}>
                  <p class="text-xs text-error">{renameError()}</p>
                </Show>
                <div class="flex items-center justify-end gap-2">
                  <Popover.Close class="btn btn-ghost btn-sm" disabled={isRenaming()} onClick={clearRenamePopover}>
                    Cancel
                  </Popover.Close>
                  <button
                    type="button"
                    class={locationToolbarActionButtonClass + " " + (isRenaming() ? "btn-disabled" : "btn-primary")}
                    disabled={isRenaming() || !renameDraft().trim()}
                    onClick={() => void saveRename().then((didSave) => {
                      if (didSave) {
                        popover.setOpen(false);
                      }
                    })}
                  >
                    {isRenaming() ? "Saving..." : "Save"}
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

export default LocationOverviewCardRenamePopover;
