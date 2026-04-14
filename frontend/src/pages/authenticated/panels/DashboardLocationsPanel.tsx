import {A, action, useAction, useSubmission} from "@solidjs/router";
import {For, Show, createEffect, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {setFavoriteLocationId} from "../../../util/common/favoriteLocation";
import {LocationSummary} from "../../../types/Types";
import {useLocations} from "../../../context/LocationContext";
import {
  runCreateLocationAction,
  runRenameLocationAction,
  sortLocationsByName
} from "../../../util/location/dashboardLocationsActions";

export const DashboardLocationsPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();

  const canEditLocations = () => {
    const role = profileContext.profile()?.role;
    return role === "admin" || role === "partner";
  };

  const canCreateLocations = () => profileContext.profile()?.role === "admin";

  const locationContext = useLocations();
  const locations = locationContext.locations;
  const [draftNames, setDraftNames] = createSignal<Record<number, string>>({});
  const [newLocationName, setNewLocationName] = createSignal("");

  const updateDraftName = (locationId: number, value: string) => {
    setDraftNames((current) => ({
      ...current,
      [locationId]: value
    }));
  };

  const getDraftName = (location: LocationSummary) =>
    draftNames()[location.id] ?? location.name;

  const renameLocationAction = action(async (
    locationId: number,
    nextName: string
  ) => runRenameLocationAction(host, locationId, nextName), "renameLocation");

  const createLocationAction = action(async (
    nextName: string
  ) => runCreateLocationAction(host, nextName), "createLocation");

  const submitRenameLocation = useAction(renameLocationAction);
  const renameLocationSubmission = useSubmission(renameLocationAction);
  const submitCreateLocation = useAction(createLocationAction);
  const createLocationSubmission = useSubmission(createLocationAction);

  createEffect(() => {
    const result = renameLocationSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok && result.updatedLocation) {
      locationContext.mutate((current) =>
        current?.map((candidate) => (
          candidate.id === result.updatedLocation!.id ? result.updatedLocation! : candidate
        ))
      );
      setDraftNames((current) => {
        const next = {
          ...current
        };
        delete next[result.locationId];
        return next;
      });
      toast.success("Location updated.");
    } else {
      toast.error(result.message ?? "Unable to update location name.");
    }

    renameLocationSubmission.clear();
  });

  createEffect(() => {
    const result = createLocationSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok && result.createdLocation) {
      locationContext.mutate((current) => sortLocationsByName([...(current ?? []), result.createdLocation!]));
      setNewLocationName("");
      toast.success("Location created.");
    } else {
      toast.error(result.message ?? "Unable to create location.");
    }

    createLocationSubmission.clear();
  });

  const savingLocationId = () =>
    renameLocationSubmission.pending
      ? renameLocationSubmission.input[0] as number
      : null;

  /**
   * Renames a location with the current draft value.
   *
   * Endpoint: `PUT /api/core/locations/{locationId}`
   * Body: `{ name }`
   *
   * @param locationId Location id to update.
   */
  const submitRenameLocationChange = (locationId: number) => {
    if (!canEditLocations() || renameLocationSubmission.pending) {
      return;
    }

    const location = locations()?.find((candidate) => candidate.id === locationId);
    if (!location) {
      return;
    }

    const nextName = getDraftName(location).trim();
    if (!nextName || nextName === location.name) {
      return;
    }

    void submitRenameLocation(locationId, nextName);
  };

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

  const saveFavorite = (locationId: number) => {
    setFavoriteLocationId(locationId);
    toast.success("Favorite location updated.");
  };

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

      <Show when={!locations.loading} fallback={<p class="text-base-content/70">Loading locations...</p>}>
        <Show when={!locations.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load locations.</p>
            <button type="button" class="btn btn-outline" onClick={() => void locationContext.refetch()}>
              Retry
            </button>
          </div>
        }>
          <Show when={(locations()?.length ?? 0) > 0} fallback={
              <div class="space-y-3">
                  <p class="text-base-content/70">No locations available.</p>
                  <button type="button" class="btn btn-outline" onClick={() => void locationContext.refetch()}>
                      Retry
                  </button>
              </div>
          }>
              <ul class="space-y-3">
                  <For each={locations()}>
                      {(location) => (
                          <li class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
                              <div class="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <A href={`/dashboard/locations/${location.id}`} class="link link-primary text-lg font-medium" preload>
                          {location.name}
                        </A>
                        <p class="text-sm text-base-content/60">
                          Updated {new Date(location.updatedAt).toLocaleString()}
                        </p>
                      </div>
                      <button
                        type="button"
                        class="btn btn-sm btn-outline"
                        onClick={() => saveFavorite(location.id)}
                      >
                        Favorite
                      </button>
                    </div>

                    <Show when={canEditLocations()}>
                      <div class="mt-3 flex flex-wrap items-center gap-2">
                        <input
                          type="text"
                          class="input input-bordered input-sm flex-1 min-w-52"
                          value={getDraftName(location)}
                          maxlength={128}
                          onInput={(event) => updateDraftName(location.id, event.currentTarget.value)}
                        />
                        <button
                          type="button"
                          class="btn btn-sm btn-primary"
                          disabled={savingLocationId() !== null}
                          onClick={() => void submitRenameLocationChange(location.id)}
                        >
                          {savingLocationId() === location.id ? "Saving..." : "Rename"}
                        </button>
                      </div>
                    </Show>
                  </li>
                )}
              </For>
            </ul>
          </Show>
        </Show>
      </Show>
    </div>
  );
};
