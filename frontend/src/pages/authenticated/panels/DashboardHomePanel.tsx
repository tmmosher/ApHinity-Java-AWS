import {Show, createEffect, createSignal, createResource} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {
  getFavoriteLocationId,
  hasSelectableFavoriteLocation,
  setFavoriteLocationId
} from "../../../util/favoriteLocation";
import {A} from "@solidjs/router";
import {useLocations} from "../../../context/LocationContext";
import {fetchLocationById} from "../../../util/locationDetailApi";

const roleLabel: Record<"admin" | "partner" | "client", string> = {
  admin: "Admin",
  partner: "Partner",
  client: "Client"
};

export const DashboardHomePanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const locationContext = useLocations();
  const locations = locationContext.locations();
  const [favoriteLocationId, setFavoriteLocationIdSignal] = createSignal(getFavoriteLocationId());
  const canAccess = () => Boolean(profileContext.profile()?.verified);

    createEffect(() => {
    if (!canAccess()) {
      return;
    }
    const locationList = locations;
    const currentFavoriteId = favoriteLocationId();
    if (!locationList || !currentFavoriteId) {
      return;
    }

    if (!hasSelectableFavoriteLocation(currentFavoriteId, locationList)) {
      setFavoriteLocationIdSignal("");
      setFavoriteLocationId("");
    }
  });

  const [location, {refetch}] = createResource(
    () => fetchLocationById(host, favoriteLocationId())
  );

  const roleDescription = () => {
    const profile = profileContext.profile();
    if (!profile) {
      return "Loading account...";
    }
    return `Signed in as ${roleLabel[profile.role]}.`;
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
          when={canAccess()}
          fallback={
            <p class="mt-2 text-sm text-base-content/70">
              Verify your email to view and select locations.
            </p>
          }
        >
          <Show when={!location.loading} fallback={<p class="mt-4 text-sm text-base-content/70">Loading locations...</p>}>
              <Show when={!location.error} fallback={
                  <div class="mt-4 space-y-3">
                      <p class="text-error">Unable to load locations.</p>
                      <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
                          Retry
                      </button>
                  </div>
              }>
                  <Show when={location()} fallback={
                      <p class="mt-4 text-sm text-base-content/70">No locations available.</p>
                  }>
                      <A href={`/dashboard/locations/${location()!.id}`} class="link link-primary text-lg font-medium"
                         preload>
                          {location()!.name}
                      </A>
                  </Show>
              </Show>
          </Show>
        </Show>
      </section>
    </div>
  );
};
