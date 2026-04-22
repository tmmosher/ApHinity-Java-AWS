import Popover from "corvu/popover";
import {createMemo, createSignal, Show} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../context/ApiHostContext";
import {useLocationDetail} from "../../context/LocationDetailContext";
import {useProfile} from "../../context/ProfileContext";
import {
  subscribeToLocationAlerts,
  unsubscribeFromLocationAlerts,
  updateLocationWorkOrderEmail
} from "../../util/common/locationApi";
import {parseOptionalWorkOrderEmail} from "../../util/common/apiSchemas";
import {canEditLocationGraphs} from "../../util/common/profileAccess";
import {
  locationToolbarActionButtonClass,
  locationToolbarPopoverClass
} from "./locationToolbarStyles";

const bellUpdatingIcon = (
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
    class="lucide lucide-bell-icon lucide-bell"
  >
    <path d="M10.268 21a2 2 0 0 0 3.464 0" />
    <path d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326" />
  </svg>
);

const bellOffIcon = (
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
    class="lucide lucide-bell-off-icon lucide-bell-off"
  >
    <path d="M10.268 21a2 2 0 0 0 3.464 0" />
    <path d="M17 17H4a1 1 0 0 1-.74-1.673C4.59 13.956 6 12.499 6 8a6 6 0 0 1 .258-1.742" />
    <path d="m2 2 20 20" />
    <path d="M8.668 3.01A6 6 0 0 1 18 8c0 2.687.77 4.653 1.707 6.05" />
  </svg>
);

const bellRingIcon = (
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
    class="lucide lucide-bell-ring-icon lucide-bell-ring"
  >
    <path d="M10.268 21a2 2 0 0 0 3.464 0" />
    <path d="M22 8c0-2.3-.8-4.3-2-6" />
    <path d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326" />
    <path d="M4 2C2.8 3.7 2 5.7 2 8" />
  </svg>
);

type LocationWorkOrderActionsProps = {
  class?: string;
};

export const LocationWorkOrderActions = (props: LocationWorkOrderActionsProps) => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {location, refetchLocation} = useLocationDetail();

  const [workOrderEmailDraft, setWorkOrderEmailDraft] = createSignal("");
  const [workOrderEmailError, setWorkOrderEmailError] = createSignal("");
  const [isSavingWorkOrderEmail, setIsSavingWorkOrderEmail] = createSignal(false);
  const [isUpdatingLocationAlerts, setIsUpdatingLocationAlerts] = createSignal(false);

  const canManageWorkOrderEmail = createMemo(() =>
    canEditLocationGraphs(profileContext.profile()?.role)
  );
  const canToggleLocationAlerts = createMemo(() => profileContext.profile()?.verified === true);
  const isHeaderActionBusy = createMemo(() => isSavingWorkOrderEmail() || isUpdatingLocationAlerts());

  const clearWorkOrderEmailPopover = () => {
    setWorkOrderEmailError("");
    setWorkOrderEmailDraft("");
  };

  const saveWorkOrderEmail = async (): Promise<boolean> => {
    if (isHeaderActionBusy() || !canManageWorkOrderEmail()) {
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

  const toggleLocationAlerts = async (): Promise<void> => {
    if (isHeaderActionBusy() || !canToggleLocationAlerts()) {
      return;
    }

    const currentLocation = location();
    if (!currentLocation) {
      return;
    }

    setIsUpdatingLocationAlerts(true);

    try {
      const updatedLocation = currentLocation.alertsSubscribed
        ? await unsubscribeFromLocationAlerts(host, currentLocation.id)
        : await subscribeToLocationAlerts(host, currentLocation.id);
      try {
        await refetchLocation();
      } catch {
        toast.error("Alert subscription changed, but location details could not refresh. Please refresh the page.");
      }
      toast.success(updatedLocation.alertsSubscribed
        ? "Location alert subscription enabled."
        : "Location alert subscription disabled.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update location alert subscription.");
    } finally {
      setIsUpdatingLocationAlerts(false);
    }
  };

  const triggerLabel = () => {
    const currentLocation = location();
    if (isSavingWorkOrderEmail()) {
      return "Saving...";
    }
    return currentLocation?.workOrderEmail ? "Update Work Order Email" : "Add Work Order Email";
  };

  return (
    <div class={props.class ?? "flex flex-wrap items-center justify-end gap-2"}>
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
            class={locationToolbarActionButtonClass + " " + (isHeaderActionBusy() ? "btn-disabled" : "btn-outline")}
            disabled={isHeaderActionBusy()}
            aria-label="Work order email settings"
          >
            {triggerLabel()}
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
                    disabled={isHeaderActionBusy()}
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
                    disabled={isHeaderActionBusy()}
                    onClick={clearWorkOrderEmailPopover}
                  >
                    Cancel
                  </Popover.Close>
                  <button
                    type="button"
                    class={locationToolbarActionButtonClass + " " + (isHeaderActionBusy() ? "btn-disabled" : "btn-primary")}
                    disabled={isHeaderActionBusy()}
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
      <Show when={canToggleLocationAlerts()}>
        <button
          type="button"
          class={locationToolbarActionButtonClass + " " + (isHeaderActionBusy() ? "btn-disabled" : "btn-outline")}
          disabled={isHeaderActionBusy()}
          aria-label={
            isUpdatingLocationAlerts()
              ? "Updating location alerts"
              : (location()?.alertsSubscribed
                ? "Unsubscribe from location alerts"
                : "Subscribe to location alerts")
          }
          onClick={() => void toggleLocationAlerts()}
        >
          {isUpdatingLocationAlerts()
            ? <>{bellUpdatingIcon}</>
            : (
              location()?.alertsSubscribed
                ? <>{bellOffIcon}</>
                : <>{bellRingIcon}</>
            )}
        </button>
      </Show>
    </div>
  );
};

export default LocationWorkOrderActions;
