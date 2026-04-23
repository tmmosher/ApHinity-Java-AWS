import {Show, createEffect, createMemo, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useLocations} from "../../../context/LocationContext";
import {useProfile} from "../../../context/ProfileContext";
import {canEditLocationGraphs} from "../../../util/common/profileAccess";
import {
  getFavoriteLocationId,
  hasSelectableFavoriteLocation,
  setFavoriteLocationId
} from "../../../util/common/favoriteLocation";
import {
  getQuickAccessLocations,
  getRecentLocationIds
} from "../../../util/common/recentLocation";
import {LocationOverviewGrid} from "../../../components/location/LocationOverviewGrid";
import {createRenameLocationHandler} from "../../../util/location/locationOverviewActions";

const roleLabel: Record<"admin" | "partner" | "client", string> = {
  admin: "Admin",
  partner: "Partner",
  client: "Client"
};

export const DashboardHomePanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const locationContext = useLocations();
  const locations = locationContext.locations;
  const [favoriteLocationId, setFavoriteLocationIdSignal] = createSignal(getFavoriteLocationId());
  const canAccess = () => Boolean(profileContext.profile()?.verified);
  const canEditLocations = () => canEditLocationGraphs(profileContext.profile()?.role);
  const quickAccessLocations = createMemo(() =>
    getQuickAccessLocations(
      locations() ?? [],
      favoriteLocationId(),
      getRecentLocationIds()
    )
  );

  createEffect(() => {
    if (!canAccess()) {
      return;
    }
    const locationList = locations();
    const currentFavoriteId = favoriteLocationId();
    if (!locationList || !currentFavoriteId) {
      return;
    }

    if (!hasSelectableFavoriteLocation(currentFavoriteId, locationList)) {
      setFavoriteLocationIdSignal("");
      setFavoriteLocationId("");
    }
  });

  const roleDescription = () => {
    const profile = profileContext.profile();
    if (!profile) {
      return "Loading account...";
    }
    return `Signed in as ${roleLabel[profile.role]}.`;
  };

  const saveFavorite = (locationId: number | null) => {
    if (locationId === null) {
      setFavoriteLocationId("");
      setFavoriteLocationIdSignal("");
      toast.success("Favorite location cleared.");
      return;
    }
    setFavoriteLocationId(locationId);
    setFavoriteLocationIdSignal(String(locationId));
    toast.success("Favorite location updated.");
  };
  const renameLocation = createRenameLocationHandler(host, locationContext.mutate);

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Dashboard</h1>
        <p class="text-base-content/70">{roleDescription()}</p>
      </header>

      <Show
        when={canAccess()}
        fallback={
          <p class="text-sm text-base-content/70">
            Verify your email to view and select locations.
          </p>
        }
      >
        <LocationOverviewGrid
          title="Quick access"
          description="Your favorite location first, followed by your three most recent locations."
          locations={locations}
          favoriteLocationId={favoriteLocationId()}
          canEditLocations={canEditLocations()}
          displayLocations={quickAccessLocations()}
          emptyMessage="No quick-access locations yet. Open a location to add it here."
          onFavorite={saveFavorite}
          onRename={renameLocation}
          onRetry={() => void locationContext.refetch()}
        />
      </Show>
    </div>
  );
};
