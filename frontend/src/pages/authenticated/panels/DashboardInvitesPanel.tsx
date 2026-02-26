import {For, Show, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {ActiveInvite} from "../../../types/Types";
import {fetchActiveInvites, processLocationInvite} from "../../../util/inviteApi";

export const DashboardInvitesPanel = () => {
  const host = useApiHost();
  const [processingInviteId, setProcessingInviteId] = createSignal<number | null>(null);

  /**
   * Retrieves pending invites for the signed-in user.
   *
   * Endpoint: `GET /api/core/location-invites/active`
   */
  const fetchInvites = async (): Promise<ActiveInvite[]> =>
    fetchActiveInvites(host);

  const [invites, {refetch}] = createResource(fetchInvites);

  /**
   * Accepts or declines a pending invite.
   *
   * Endpoint: `POST /api/core/location-invites/{inviteId}/{action}`
   *
   * @param inviteId Invite identifier.
   * @param action Decision to apply (`accept` or `decline`).
   */
  const processInvite = async (inviteId: number, action: "accept" | "decline") => {
    if (processingInviteId() !== null) {
      return;
    }
    setProcessingInviteId(inviteId);
    try {
      await processLocationInvite(host, inviteId, action);
      toast.success(action === "accept" ? "Invite accepted." : "Invite declined.");
      await refetch();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update invite.");
    } finally {
      setProcessingInviteId(null);
    }
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">Invites</h1>
        <p class="text-base-content/70">
          Review pending location invites and decide whether to accept access.
        </p>
      </header>

      <Show when={!invites.loading} fallback={<p class="text-base-content/70">Loading invites...</p>}>
        <Show when={!invites.error} fallback={
          <div class="space-y-3">
            <p class="text-error">Unable to load invites.</p>
            <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
              Retry
            </button>
          </div>
        }>
          <Show when={(invites()?.length ?? 0) > 0} fallback={
              <div class="space-y-3">
                  <p class="text-base-content/70">No active invites.</p>
                  <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
                      Retry
                  </button>
              </div>
          }>
              <ul class="space-y-3">
                  <For each={invites()}>
                {(invite) => (
                  <li class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
                    <div class="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p class="font-semibold">
                          {invite.locationName ?? `Location #${invite.locationId}`}
                        </p>
                        <p class="text-sm text-base-content/60">
                          Expires {new Date(invite.expiresAt).toLocaleString()}
                        </p>
                      </div>
                      <div class="flex gap-2">
                        <button
                          type="button"
                          class="btn btn-sm btn-primary"
                          disabled={processingInviteId() !== null}
                          onClick={() => void processInvite(invite.id, "accept")}
                        >
                          {processingInviteId() === invite.id ? "Working..." : "Accept"}
                        </button>
                        <button
                          type="button"
                          class="btn btn-sm btn-outline"
                          disabled={processingInviteId() !== null}
                          onClick={() => void processInvite(invite.id, "decline")}
                        >
                          Decline
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
    </div>
  );
};
