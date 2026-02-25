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
import {A} from "@solidjs/router";

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

  /**
   * Loads locations accessible by the authenticated user.
   * TODO memoize this as this is done frequently
   *
   * Endpoint: `GET /api/core/locations`
   *
   * @returns Parsed location summaries for dashboard selection UI.
   * @throws {Error} When the backend responds with non-OK status.
   */
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
                  <A href={`/dashboard/locations/${location.id}`} class="link link-primary text-lg font-medium" preload>
                      {location.name}
                  </A>
              </Show>
            </Show>
          </Show>
        </Show>
      </section>
    </div>
  );
};
