import {action, useAction, useSubmission} from "@solidjs/router";
import {For, Show, createEffect, createMemo, createResource, createSignal} from "solid-js";
import {useApiHost} from "../../../context/ApiHostContext";
import {parseLocationMembershipList} from "../../../util/common/coreApi";
import {apiFetch} from "../../../util/common/apiFetch";
import {ActionResult, LocationMembershipWithStatus} from "../../../types/Types";
import {useLocations} from "../../../context/LocationContext";

type ApplyDeleteQueueActionResult = ActionResult;

export const DashboardPermissionsPanel = () => {
  const host = useApiHost();
  const [selectedLocationId, setSelectedLocationId] = createSignal("");
  const {locations, refetch: refetchLocations} = useLocations();

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
  const fetchMemberships = async (locationId: string): Promise<LocationMembershipWithStatus[]> => {
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

  const [memberships, {refetch: refetchMemberships, mutate: mutateMemberships}] = createResource(selectedLocationId, fetchMemberships);
  const [queueError, setQueueError] = createSignal("");

  const removeMembership = async (locationId: string, userId: number) => {
    const response = await apiFetch(host + "/api/core/locations/" + locationId + "/memberships/" + userId, {
      method: "DELETE"
    });
    if (!response.ok) {
      throw new Error("Unable to remove membership");
    }
  };

  const deleteQueue = createMemo(() =>
    memberships()?.filter((membership) => !membership.active) ?? []
  );
  const deleteQueueHasItems = createMemo(() => deleteQueue().length > 0);

  const applyDeleteQueueAction = action(async (
    locationId: string,
    userIds: number[]
  ): Promise<ApplyDeleteQueueActionResult> => {
    try {
      await Promise.all(
        userIds.map((userId) => removeMembership(locationId, userId))
      );
      return {
        ok: true
      };
    } catch {
      return {
        ok: false,
        message: "Unable to apply membership removals."
      };
    }
  }, "applyMembershipDeleteQueue");

  const submitDeleteQueue = useAction(applyDeleteQueueAction);
  const deleteQueueSubmission = useSubmission(applyDeleteQueueAction);

  createEffect(() => {
    const result = deleteQueueSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok) {
      void refetchMemberships();
    } else {
      setQueueError(result.message ?? "Unable to apply membership removals.");
    }

    deleteQueueSubmission.clear();
  });

  const resetDeleteQueue = () => {
    if (!deleteQueueHasItems()) {
      return;
    }
    mutateMemberships((previousMemberships) =>
      previousMemberships?.map((membership) => ({...membership, active: true}))
    );
    setQueueError("");
  };

  const applyDeleteQueue = () => {
    if (deleteQueueSubmission.pending || !deleteQueueHasItems()) {
      return;
    }

    const locationId = selectedLocationId();
    if (!locationId) {
      return;
    }

    setQueueError("");
    void submitDeleteQueue(
      locationId,
      deleteQueue().map((membership) => membership.membership.userId)
    );
  };

  const addMembershipToRemoveQueue = (membership: LocationMembershipWithStatus) => {
      mutateMemberships((prev) => {
        if (!prev) return;
        const next = [...prev];
        const index = next.findIndex((candidate) => candidate.membership.userId === membership.membership.userId);
        if (index !== -1) {
          next[index] = {...membership, active: false};
        }
        return next;
      });
  }

  createEffect(() => {
    selectedLocationId();
    setQueueError("");
  });

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
                    <div class="mb-3 flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        class={"btn btn-sm " + (deleteQueueHasItems() && !deleteQueueSubmission.pending ? "btn-error" : "btn-disabled")}
                        disabled={!deleteQueueHasItems() || deleteQueueSubmission.pending}
                        onClick={() => void applyDeleteQueue()}
                      >
                        {deleteQueueSubmission.pending ? "Applying..." : "Apply"}
                      </button>
                      <button
                        type="button"
                        class={"btn btn-sm " + (deleteQueueHasItems() && !deleteQueueSubmission.pending ? "btn-outline" : "btn-disabled")}
                        disabled={!deleteQueueHasItems() || deleteQueueSubmission.pending}
                        onClick={resetDeleteQueue}
                      >
                        Undo
                      </button>
                      <Show when={deleteQueueHasItems()}>
                        <span class="text-xs text-base-content/70">
                          {deleteQueue().length} pending removal{deleteQueue().length === 1 ? "" : "s"}
                        </span>
                      </Show>
                    </div>

                    <Show when={queueError()}>
                      <p class="mb-3 text-sm text-error">{queueError()}</p>
                    </Show>

                    <ul class="space-y-3">
                      <For each={memberships()?.filter((membership) => membership.active)}>
                        {(membership) => (
                          <li class="rounded-lg border border-base-300 p-3">
                            <div class="flex flex-wrap items-center justify-between gap-3">
                              <div class="hover:bg-red-500 transition duration-300 ease-in-out hover:cursor-pointer" onClick={() => void addMembershipToRemoveQueue(membership)}>
                                <p class="font-medium">{membership.membership.userEmail ?? `User #${membership.membership.userId}`}</p>
                                <p class="text-xs text-base-content/60">
                                  User ID {membership.membership.userId}
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
