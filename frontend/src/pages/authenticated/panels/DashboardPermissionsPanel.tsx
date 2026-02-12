import {For, Show, createEffect, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {
  parseLocationList,
  parseLocationMembershipList
} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";
import {LocationMemberRole, LocationMembership, LocationSummary} from "../../../types/Types";

export const DashboardPermissionsPanel = () => {
  const host = useApiHost();
  const [selectedLocationId, setSelectedLocationId] = createSignal("");
  const [draftRoles, setDraftRoles] = createSignal<Record<string, LocationMemberRole>>({});
  const [savingUserId, setSavingUserId] = createSignal<number | null>(null);

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

  const [memberships, {mutate, refetch: refetchMemberships}] = createResource(selectedLocationId, fetchMemberships);

  createEffect(() => {
    selectedLocationId();
    setDraftRoles({});
  });

  const toDraftRoleKey = (locationId: string, userId: number): string => locationId + ":" + userId;

  const getDraftRole = (membership: LocationMembership): LocationMemberRole =>
    draftRoles()[toDraftRoleKey(selectedLocationId(), membership.userId)] ?? membership.userRole;

  const updateDraftRole = (userId: number, role: LocationMemberRole) => {
    const locationId = selectedLocationId();
    if (!locationId) {
      return;
    }
    setDraftRoles((current) => ({
      ...current,
      [toDraftRoleKey(locationId, userId)]: role
    }));
  };

  const saveRole = async (membership: LocationMembership) => {
    if (savingUserId() !== null || !selectedLocationId()) {
      return;
    }

    const nextRole = getDraftRole(membership);
    if (nextRole === membership.userRole) {
      return;
    }

    setSavingUserId(membership.userId);
    try {
      const response = await apiFetch(
        host + "/api/core/locations/" + selectedLocationId() + "/memberships/" + membership.userId,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            userRole: nextRole
          })
        }
      );
      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        toast.error(errorBody?.message ?? "Unable to update membership role.");
        return;
      }

      mutate((current) =>
        current?.map((candidate) =>
          candidate.userId === membership.userId ? {
            ...candidate,
            userRole: nextRole
          } : candidate
        )
      );
      setDraftRoles((current) => {
        const next = {
          ...current
        };
        delete next[toDraftRoleKey(selectedLocationId(), membership.userId)];
        return next;
      });
      toast.success("Membership updated.");
    } catch {
      toast.error("Unable to update membership role.");
    } finally {
      setSavingUserId(null);
    }
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Permissions</h1>
        <p class="text-base-content/70">
          Manage user roles for each location.
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
                              <div class="flex items-center gap-2">
                                <select
                                  class="select select-bordered select-sm"
                                  value={getDraftRole(membership)}
                                  onChange={(event) => updateDraftRole(membership.userId, event.currentTarget.value as LocationMemberRole)}
                                >
                                  <option value="admin">Admin</option>
                                  <option value="partner">Partner</option>
                                  <option value="client">Client</option>
                                </select>
                                <button
                                  type="button"
                                  class="btn btn-sm btn-primary"
                                  disabled={savingUserId() !== null}
                                  onClick={() => void saveRole(membership)}
                                >
                                  {savingUserId() === membership.userId ? "Saving..." : "Save"}
                                </button>
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
