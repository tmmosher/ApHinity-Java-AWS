import {Show, createEffect, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {parseLocationList} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";
import {
  getFavoriteLocationId,
  getFavoriteLocationIdForSave,
  hasSelectableFavoriteLocation,
  setFavoriteLocationId
} from "../../../util/favoriteLocation";
import {LocationSummary} from "../../../types/Types";

const roleLabel: Record<"admin" | "partner" | "client", string> = {
  admin: "Admin",
  partner: "Partner",
  client: "Client"
};

export const DashboardHomePanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const [favoriteLocationId, setFavoriteLocationIdSignal] = createSignal(getFavoriteLocationId());
  const canAccessLocations = () => Boolean(profileContext.profile()?.verified);

  const fetchLocations = async (): Promise<LocationSummary[]> => {
    const response = await apiFetch(host + "/api/core/locations", {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load locations.");
    }
    return parseLocationList(await response.json());
  };

  const [locations, {refetch}] = createResource(
    () => (canAccessLocations() ? "verified" : null),
    () => fetchLocations()
  );

  createEffect(() => {
    if (!canAccessLocations()) {
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

  const saveFavoriteLocation = (event: SubmitEvent) => {
    event.preventDefault();
    const locationIdToSave = getFavoriteLocationIdForSave(favoriteLocationId(), locations());
    if (locationIdToSave === null) {
      toast.error("Select a location first.");
      return;
    }

    setFavoriteLocationId(locationIdToSave);
    toast.success("Favorite location updated.");
  };

  const clearFavoriteLocation = () => {
    setFavoriteLocationIdSignal("");
    setFavoriteLocationId("");
    toast.success("Favorite location cleared.");
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Dashboard</h1>
        <p class="text-base-content/70">{roleDescription()}</p>
      </header>

      <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
        <h2 class="text-lg font-semibold">Favorite location</h2>
        <Show
          when={canAccessLocations()}
          fallback={
            <p class="mt-2 text-sm text-base-content/70">
              Verify your email to view and select locations.
            </p>
          }
        >
          <p class="mt-1 text-sm text-base-content/70">
            Select one of your locations for quick recall in your home dashboard.
          </p>

          <Show when={!locations.loading} fallback={<p class="mt-4 text-sm text-base-content/70">Loading locations...</p>}>
            <Show when={!locations.error} fallback={
              <div class="mt-4 space-y-3">
                <p class="text-error">Unable to load locations.</p>
                <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
                  Retry
                </button>
              </div>
            }>
              <Show when={(locations()?.length ?? 0) > 0} fallback={
                <p class="mt-4 text-sm text-base-content/70">No locations available.</p>
              }>
                <form class="mt-4 grid gap-3 sm:grid-cols-[1fr_auto_auto]" onSubmit={saveFavoriteLocation}>
                  <select
                    class="select select-bordered w-full"
                    value={favoriteLocationId()}
                    onChange={(event) => setFavoriteLocationIdSignal(event.currentTarget.value)}
                  >
                    <option value="">Select a location</option>
                    {locations()?.map((location) => (
                      <option value={String(location.id)}>{location.name}</option>
                    ))}
                  </select>
                  <button type="submit" class="btn btn-primary">
                    Save
                  </button>
                  <button type="button" class="btn btn-outline" onClick={clearFavoriteLocation}>
                    Clear
                  </button>
                </form>
              </Show>
            </Show>
          </Show>
        </Show>
      </section>
    </div>
  );
};
