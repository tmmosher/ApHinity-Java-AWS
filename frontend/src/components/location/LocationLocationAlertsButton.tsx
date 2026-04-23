import {createEffect, createMemo, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../context/ApiHostContext";
import {useLocationDetail} from "../../context/LocationDetailContext";
import {useProfile} from "../../context/ProfileContext";
import {
  subscribeToLocationAlerts,
  unsubscribeFromLocationAlerts
} from "../../util/common/locationApi";
import {locationToolbarActionButtonClass} from "./locationToolbarStyles";

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

export const LocationLocationAlertsButton = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const {location, refetchLocation} = useLocationDetail();

  const [isUpdatingLocationAlerts, setIsUpdatingLocationAlerts] = createSignal(false);

  const canToggleLocationAlerts = createMemo(() => profileContext.profile()?.verified === true);

  const toggleLocationAlerts = async (): Promise<void> => {
    if (isUpdatingLocationAlerts() || !canToggleLocationAlerts()) {
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
        toast.error("Alert subscription changed, but location details could not refresh. Please refresh the page");
      }
      toast.success(updatedLocation.alertsSubscribed
        ? "Location alert subscription enabled"
        : "Location alert subscription disabled");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update location alert subscription");
    } finally {
      setIsUpdatingLocationAlerts(false);
    }
  };

  if (!canToggleLocationAlerts()) {
    return null;
  }

  const [isSubscribed, setIsSubscribed] = createSignal<boolean>(location()?.alertsSubscribed === true)
  const [buttonTitle, setButtonTitle] = createSignal<string>("");
  const [hoverClass, setHoverClass] = createSignal<string>("");

  createEffect(() => {
    setIsSubscribed(location()?.alertsSubscribed === true);
    setButtonTitle(isUpdatingLocationAlerts()
      ? "Updating location alerts"
      : (isSubscribed()
        ? "Unsubscribe"
        : "Subscribe"));
    setHoverClass(isSubscribed()
      ? "hover:bg-error hover:border-error hover:text-error-content"
      : "hover:bg-green-500 hover:border-green-500 hover:text-white");
  });

  return (
    <button
      type="button"
      class={
        locationToolbarActionButtonClass +
        " btn-outline " +
        (isUpdatingLocationAlerts() ? "btn-disabled" : hoverClass())
      }
      disabled={isUpdatingLocationAlerts()}
      title={buttonTitle()}
      aria-label={buttonTitle()}
      onClick={() => void toggleLocationAlerts()}
    >
      {isUpdatingLocationAlerts()
        ? <>{bellUpdatingIcon}</>
        : (
          isSubscribed()
            ? <>{bellOffIcon}</>
            : <>{bellRingIcon}</>
        )}
    </button>
  );
};

export default LocationLocationAlertsButton;
