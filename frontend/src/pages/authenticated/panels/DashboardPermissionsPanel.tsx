import {For, Show, createEffect, createResource, createSignal} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {
  parseLocationList,
  parseLocationMembershipList
} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";
import {LocationMembership, LocationSummary} from "../../../types/Types";

export const DashboardPermissionsPanel = () => {
  const host = useApiHost();
  const [selectedLocationId, setSelectedLocationId] = createSignal("");

  /**
   * Loads all locations that can be managed in the permissions panel.
   *
   * Endpoint: `GET /api/core/locations`
   */
  const fetchLocations = async (): Promise<LocationSummary[]> => {
    const response = await apiFetch(host + "/api/core/locations", {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load locations");
    }
    return parseLocationList(await response.json());
  };

  const [locations, {refetch: refetchLocations}] = createResource(fetchLocations);

  createEffect(() => {
    if (selectedLocationId()) {
      return;
    }
    const firstLocation = locations()?.[0];
    if (firstLocation) {
      setSelectedLocationId(String(firstLocation.id));
    }
  });

  /**
   * Loads current memberships for a selected location.
   *
   * Endpoint: `GET /api/core/locations/{locationId}/memberships`
   *
   * @param locationId Selected location id as a string.
   * @returns Membership list, or empty list when no location is selected.
   */
  const fetchMemberships = async (locationId: string): Promise<LocationMembership[]> => {
    if (!locationId) {
      return [];
    }

    const response = await apiFetch(host + "/api/core/locations/" + locationId + "/memberships", {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load memberships");
    }
    return parseLocationMembershipList(await response.json());
  };

  const [memberships, {refetch: refetchMemberships}] = createResource(selectedLocationId, fetchMemberships);

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Permissions</h1>
        <p class="text-base-content/70">
          View users assigned to each location.
        </p>
      </header>

      <Show when={!locations.loading} fallback={<p class="text-base-content/70">Loading locations...</p>}>
        <Show when={!locations.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load locations.</p>
            <button type="button" class="btn btn-outline" onClick={() => void refetchLocations()}>
              Retry
            </button>
          </div>
        }>
          <Show when={(locations()?.length ?? 0) > 0} fallback={<p class="text-base-content/70">No locations available.</p>}>
            <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm space-y-4">
              <label class="form-control">
                <span class="label-text">Location</span>
                <select
                  class="select select-bordered mt-1"
                  value={selectedLocationId()}
                  onChange={(event) => setSelectedLocationId(event.currentTarget.value)}
                >
                  {locations()?.map((location) => (
                    <option value={String(location.id)}>{location.name}</option>
                  ))}
                </select>
              </label>

              <Show when={!memberships.loading} fallback={<p class="text-base-content/70">Loading permissions...</p>}>
                <Show when={!memberships.error} fallback={
                  <div class="space-y-2">
                    <p class="text-error">Unable to load location memberships.</p>
                    <button type="button" class="btn btn-outline" onClick={() => void refetchMemberships()}>
                      Retry
                    </button>
                  </div>
                }>
                  <Show when={(memberships()?.length ?? 0) > 0} fallback={
                    <p class="text-base-content/70">No users are assigned to this location yet.</p>
                  }>
                    <ul class="space-y-3">
                      <For each={memberships()}>
                        {(membership) => (
                          <li class="rounded-lg border border-base-300 p-3">
                            <div class="flex flex-wrap items-center justify-between gap-3">
                              <div>
                                <p class="font-medium">{membership.userEmail ?? `User #${membership.userId}`}</p>
                                <p class="text-xs text-base-content/60">
                                  User ID {membership.userId}
                                </p>
                              </div>
                            </div>
                          </li>
                        )}
                      </For>
                    </ul>
                  </Show>
                </Show>
              </Show>
            </section>
          </Show>
        </Show>
      </Show>
    </div>
  );
};
