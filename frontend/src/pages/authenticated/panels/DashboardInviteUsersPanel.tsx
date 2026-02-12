import {Show, createEffect, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {LocationSummary, parseLocationList} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";

export const DashboardInviteUsersPanel = () => {
  const host = useApiHost();
  const [invitedEmail, setInvitedEmail] = createSignal("");
  const [selectedLocationId, setSelectedLocationId] = createSignal("");
  const [isSubmitting, setIsSubmitting] = createSignal(false);

  const fetchLocations = async (): Promise<LocationSummary[]> => {
    const response = await apiFetch(host + "/api/core/locations", {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load locations");
    }
    return parseLocationList(await response.json());
  };

  const [locations, {refetch}] = createResource(fetchLocations);

  createEffect(() => {
    if (selectedLocationId()) {
      return;
    }
    const firstLocation = locations()?.[0];
    if (firstLocation) {
      setSelectedLocationId(String(firstLocation.id));
    }
  });

  const submitInvite = async (event: SubmitEvent) => {
    event.preventDefault();
    if (isSubmitting()) {
      return;
    }

    const locationId = Number(selectedLocationId());
    if (!Number.isFinite(locationId) || locationId <= 0) {
      toast.error("Select a location first.");
      return;
    }

    const normalizedEmail = invitedEmail().trim().toLowerCase();
    if (!normalizedEmail) {
      toast.error("Invite email is required.");
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await apiFetch(host + "/api/core/location-invites", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          locationId,
          invitedEmail: normalizedEmail
        })
      });
      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        toast.error(errorBody?.message ?? "Unable to create invite.");
        return;
      }

      setInvitedEmail("");
      toast.success("Invite created.");
    } catch {
      toast.error("Unable to create invite.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Invite users</h1>
        <p class="text-base-content/70">
          Invite users to a location by email.
        </p>
      </header>

      <Show when={!locations.loading} fallback={<p class="text-base-content/70">Loading locations...</p>}>
        <Show when={!locations.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load locations.</p>
            <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
              Retry
            </button>
          </div>
        }>
          <Show when={(locations()?.length ?? 0) > 0} fallback={<p class="text-base-content/70">No locations available.</p>}>
            <form class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm grid gap-4" onSubmit={submitInvite}>
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
              <label class="form-control">
                <span class="label-text">Invite email</span>
                <input
                  type="email"
                  class="input input-bordered mt-1"
                  value={invitedEmail()}
                  onInput={(event) => setInvitedEmail(event.currentTarget.value)}
                  placeholder="user@company.com"
                />
              </label>
              <button type="submit" class="btn btn-primary w-fit" disabled={isSubmitting()}>
                {isSubmitting() ? "Sending..." : "Send invite"}
              </button>
            </form>
          </Show>
        </Show>
      </Show>
    </div>
  );
};
