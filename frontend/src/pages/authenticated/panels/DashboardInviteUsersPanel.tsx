import {action, useSubmission} from "@solidjs/router";
import {Show, createEffect, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {ActionResult, LocationSummary} from "../../../types/Types";
import {createLocationInvite, fetchInviteableLocations} from "../../../util/common/inviteApi";

export const DashboardInviteUsersPanel = () => {
  const host = useApiHost();
  const [invitedEmail, setInvitedEmail] = createSignal("");
  const [selectedLocationId, setSelectedLocationId] = createSignal("");

  /**
   * Loads available locations for invite targeting.
   *
   * Endpoint: `GET /api/core/location-invites/locations`
   */
  const fetchLocations = async (): Promise<LocationSummary[]> =>
    fetchInviteableLocations(host);

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

  const submitInviteAction = action(async (formData: FormData): Promise<ActionResult> => {
    try {
      await createLocationInvite(
        host,
        Number(formData.get("locationId")?.toString() ?? ""),
        formData.get("invitedEmail")?.toString() ?? ""
      );
      return {
        ok: true
      };
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : "Unable to create invite"
      };
    }
  }, "submitInvite");

  const inviteSubmission = useSubmission(submitInviteAction);

  createEffect(() => {
    const result = inviteSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok) {
      setInvitedEmail("");
      toast.success("Invite created");
    } else {
      toast.error(result.message ?? "Unable to create invite");
    }

    inviteSubmission.clear();
  });

  /**
   * Creates a location invite for the selected location/email pair.
   *
   * Endpoint: `POST /api/core/location-invites`
   * Body: `{ locationId, invitedEmail }`
   *
   */
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
            <form
              class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm grid gap-4"
              method="post"
              action={submitInviteAction}
            >
              <label class="form-control">
                <span class="label-text">Location</span>
                <select
                  name="locationId"
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
                  name="invitedEmail"
                  class="input input-bordered mt-1"
                  value={invitedEmail()}
                  onInput={(event) => setInvitedEmail(event.currentTarget.value)}
                  placeholder="user@company.com"
                />
              </label>
              <button type="submit" class="btn btn-primary w-fit" disabled={inviteSubmission.pending}>
                {inviteSubmission.pending ? "Sending..." : "Send invite"}
              </button>
            </form>
          </Show>
        </Show>
      </Show>
    </div>
  );
};
