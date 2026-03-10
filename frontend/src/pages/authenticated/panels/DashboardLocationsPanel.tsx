import {A, action, useAction, useSubmission} from "@solidjs/router";
import {For, Show, createEffect, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {parseLocationSummary} from "../../../util/common/coreApi";
import {apiFetch} from "../../../util/common/apiFetch";
import {setFavoriteLocationId} from "../../../util/common/favoriteLocation";
import {ActionResult, LocationSummary} from "../../../types/Types";
import {useLocations} from "../../../context/LocationContext";

type RenameLocationActionResult = ActionResult & {
  locationId: number;
  updatedLocation?: LocationSummary;
};

export const DashboardLocationsPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();

  const canEditLocations = () => {
    const role = profileContext.profile()?.role;
    return role === "admin" || role === "partner";
  };

  const locationContext = useLocations();
  const locations = locationContext.locations;
  const [draftNames, setDraftNames] = createSignal<Record<number, string>>({});

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
  ): Promise<RenameLocationActionResult> => {
    try {
      const response = await apiFetch(host + "/api/core/locations/" + locationId, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          name: nextName
        })
      });
      if (!response.ok) {
        const errorBody = await response.json().catch(() => null) as {message?: unknown} | null;
        return {
          ok: false,
          locationId,
          message: typeof errorBody?.message === "string" ? errorBody.message : "Unable to update location name."
        };
      }

      return {
        ok: true,
        locationId,
        updatedLocation: parseLocationSummary(await response.json())
      };
    } catch {
      return {
        ok: false,
        locationId,
        message: "Unable to update location name."
      };
    }
  }, "renameLocation");

  const submitRenameLocation = useAction(renameLocationAction);
  const renameLocationSubmission = useSubmission(renameLocationAction);

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
  const renameLocation = (locationId: number) => {
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
                          onClick={() => void renameLocation(location.id)}
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
