import {action, useAction, useSubmission} from "@solidjs/router";
import {createEffect, createSignal, Show} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {useLocations} from "../../../context/LocationContext";
import {useProfile} from "../../../context/ProfileContext";
import {canEditLocationGraphs} from "../../../util/common/profileAccess";
import {LocationOverviewGrid} from "../../../components/location/LocationOverviewGrid";
import {
  getFavoriteLocationId,
  hasSelectableFavoriteLocation,
  saveFavoriteLocation,
  setFavoriteLocationId
} from "../../../util/common/favoriteLocation";
import {
  runCreateLocationAction,
  sortLocationsByName
} from "../../../util/location/dashboardLocationsActions";
import {
  createRenameLocationHandler,
  createUploadLocationThumbnailHandler
} from "../../../util/location/locationOverviewActions";
import {toast} from "solid-toast";

export const DashboardLocationsPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const locationContext = useLocations();
  const locations = locationContext.locations;
  const [favoriteLocationId, setFavoriteLocationIdSignal] = createSignal(getFavoriteLocationId());
  const [newLocationName, setNewLocationName] = createSignal("");

  const canEditLocations = () => canEditLocationGraphs(profileContext.profile()?.role);

  const canCreateLocations = () => profileContext.profile()?.role === "admin";

  createEffect(() => {
    const profile = profileContext.profile();
    if (!profile?.verified) {
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

  const createLocationAction = action(async (
    nextName: string
  ) => runCreateLocationAction(host, nextName), "createLocation");

  const submitCreateLocation = useAction(createLocationAction);
  const createLocationSubmission = useSubmission(createLocationAction);

  createEffect(() => {
    const result = createLocationSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok && result.createdLocation) {
      locationContext.mutate((current) => sortLocationsByName([...(current ?? []), result.createdLocation!]));
      setNewLocationName("");
      toast.success("Location created");
    } else {
      toast.error(result.message ?? "Unable to create location");
    }

    createLocationSubmission.clear();
  });

  const submitNewLocation = () => {
    if (!canCreateLocations() || createLocationSubmission.pending) {
      return;
    }

    const nextName = newLocationName().trim();
    if (!nextName) {
      return;
    }

    void submitCreateLocation(nextName);
  };

  const saveFavorite = (locationId: number | null) =>
    saveFavoriteLocation(locationId, setFavoriteLocationIdSignal);
  const renameLocation = createRenameLocationHandler(host, locationContext.mutate);
  const uploadLocationThumbnail = createUploadLocationThumbnailHandler(host, locationContext.mutate);

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Locations</h1>
        <p class="text-base-content/70">
          Select a location.
        </p>
      </header>

      <Show when={canCreateLocations()}>
        <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm space-y-3">
          <div>
            <p class="font-medium">Add location</p>
            <p class="text-sm text-base-content/70">
              Create a new location and make it available across the dashboard.
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <input
              type="text"
              class="input input-bordered flex-1 min-w-52"
              value={newLocationName()}
              maxlength={128}
              placeholder="New location name"
              disabled={createLocationSubmission.pending}
              onInput={(event) => setNewLocationName(event.currentTarget.value)}
            />
            <button
              type="button"
              class="btn btn-primary"
              disabled={createLocationSubmission.pending || !newLocationName().trim()}
              onClick={submitNewLocation}
            >
              {createLocationSubmission.pending ? "Creating..." : "Add location"}
            </button>
          </div>
        </section>
      </Show>

      <LocationOverviewGrid
        title="All locations"
        description="Favorite a location for faster access."
        apiHost={host}
        locations={locations}
        favoriteLocationId={favoriteLocationId()}
        canEditLocations={canEditLocations()}
        emptyMessage="No locations available."
        onFavorite={saveFavorite}
        onRename={renameLocation}
        onThumbnailUpload={uploadLocationThumbnail}
        onRetry={() => void locationContext.refetch()}
      />
    </div>
  );
};
