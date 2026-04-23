import Popover from "corvu/popover";
import {createMemo, createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../context/ApiHostContext";
import {useLocationDetail} from "../../context/LocationDetailContext";
import {useProfile} from "../../context/ProfileContext";
import {updateLocationWorkOrderEmail} from "../../util/common/locationApi";
import {parseOptionalWorkOrderEmail} from "../../util/common/apiSchemas";
import {canEditLocationGraphs} from "../../util/common/profileAccess";
import {
  locationToolbarActionButtonClass,
  locationToolbarPopoverClass
} from "./locationToolbarStyles";

export const LocationWorkOrderEmailPopover = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {location, refetchLocation} = useLocationDetail();

  const [workOrderEmailDraft, setWorkOrderEmailDraft] = createSignal("");
  const [workOrderEmailError, setWorkOrderEmailError] = createSignal("");
  const [isSavingWorkOrderEmail, setIsSavingWorkOrderEmail] = createSignal(false);

  const canManageWorkOrderEmail = createMemo(() =>
    canEditLocationGraphs(profileContext.profile()?.role)
  );

  const clearWorkOrderEmailPopover = () => {
    setWorkOrderEmailError("");
    setWorkOrderEmailDraft("");
  };

  const saveWorkOrderEmail = async (): Promise<boolean> => {
    if (isSavingWorkOrderEmail() || !canManageWorkOrderEmail()) {
      return false;
    }

    const currentLocation = location();
    if (!currentLocation) {
      return false;
    }

    let nextWorkOrderEmail: string | null;
    try {
      nextWorkOrderEmail = parseOptionalWorkOrderEmail(workOrderEmailDraft());
    } catch (error) {
      setWorkOrderEmailError(error instanceof Error ? error.message : "Unable to update work order email.");
      return false;
    }

    const currentWorkOrderEmail = currentLocation.workOrderEmail ?? "";
    if ((nextWorkOrderEmail ?? "") === currentWorkOrderEmail) {
      setWorkOrderEmailError("");
      return true;
    }

    setWorkOrderEmailError("");
    setIsSavingWorkOrderEmail(true);

    try {
      await updateLocationWorkOrderEmail(host, currentLocation.id, nextWorkOrderEmail);
      try {
        await refetchLocation();
      } catch {
        toast.error("Work order email saved, but location details could not refresh. Please refresh the page.");
      }
      toast.success(nextWorkOrderEmail ? "Work order email updated." : "Work order email cleared.");
      setWorkOrderEmailDraft(nextWorkOrderEmail ?? "");
      setWorkOrderEmailError("");
      return true;
    } catch (error) {
      setWorkOrderEmailError(error instanceof Error ? error.message : "Unable to update work order email.");
      return false;
    } finally {
      setIsSavingWorkOrderEmail(false);
    }
  };

  return (
    <Show when={canManageWorkOrderEmail()}>
      <Popover
        onOpenChange={(open) => {
          if (open) {
            setWorkOrderEmailDraft(location()?.workOrderEmail ?? "");
            setWorkOrderEmailError("");
            return;
          }
          clearWorkOrderEmailPopover();
        }}
      >
        <Popover.Trigger
          type="button"
          class={locationToolbarActionButtonClass + "w-full justify-start "+ " " + (isSavingWorkOrderEmail() ? "btn-disabled" : "btn-outline")}
          disabled={isSavingWorkOrderEmail()}
          aria-label="Work order email settings"
        >
          Work Order Email
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content class={locationToolbarPopoverClass}>
            <div class="space-y-3">
              <div class="space-y-1">
                <Popover.Label class="text-sm font-semibold">
                  Work Order Email
                </Popover.Label>
                <Popover.Description class="text-xs text-base-content/70">
                  Update the client company's email address where work orders should be sent.
                  Leave it blank to clear the value.
                </Popover.Description>
              </div>
              <label class="form-control w-full">
                <span class="label-text text-xs">Email address</span>
                <input
                  type="email"
                  class="input input-bordered input-sm w-full"
                  value={workOrderEmailDraft()}
                  disabled={isSavingWorkOrderEmail()}
                  onInput={(event) => {
                    setWorkOrderEmailDraft(event.currentTarget.value);
                    if (workOrderEmailError()) {
                      setWorkOrderEmailError("");
                    }
                  }}
                />
              </label>
              <Show when={workOrderEmailError()}>
                <p class="text-xs text-error">{workOrderEmailError()}</p>
              </Show>
              <div class="flex items-center justify-end gap-2">
                <Popover.Close
                  class="btn btn-ghost btn-sm"
                  disabled={isSavingWorkOrderEmail()}
                  onClick={clearWorkOrderEmailPopover}
                >
                  Cancel
                </Popover.Close>
                <button
                  type="button"
                  class={locationToolbarActionButtonClass + " " + (isSavingWorkOrderEmail() ? "btn-disabled" : "btn-primary")}
                  disabled={isSavingWorkOrderEmail()}
                  onClick={() => void saveWorkOrderEmail()}
                >
                  {isSavingWorkOrderEmail() ? "Saving..." : "Save"}
                </button>
              </div>
            </div>
          </Popover.Content>
        </Popover.Portal>
      </Popover>
    </Show>
  );
};

export default LocationWorkOrderEmailPopover;
