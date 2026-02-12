import {For, Show, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {parseActiveInviteList} from "../../../types/coreApi";
import {apiFetch} from "../../../util/apiFetch";
import {ActiveInvite} from "../../../types/Types";

export const DashboardInvitesPanel = () => {
  const host = useApiHost();
  const [processingInviteId, setProcessingInviteId] = createSignal<number | null>(null);

  const fetchInvites = async (): Promise<ActiveInvite[]> => {
    const response = await apiFetch(host + "/api/core/location-invites/active", {
      method: "GET"
    });
    if (!response.ok) {
      throw new Error("Unable to load invites");
    }
    return parseActiveInviteList(await response.json());
  };

  const [invites, {refetch}] = createResource(fetchInvites);

  const processInvite = async (inviteId: number, action: "accept" | "decline") => {
    if (processingInviteId() !== null) {
      return;
    }
    setProcessingInviteId(inviteId);
    try {
      const response = await apiFetch(host + "/api/core/location-invites/" + inviteId + "/" + action, {
        method: "POST",
      });
      if (!response.ok) {
        const errorBody = await response.json().catch(() => null);
        toast.error(errorBody?.message ?? "Unable to update invite.");
        return;
      }
      toast.success(action === "accept" ? "Invite accepted." : "Invite declined.");
      await refetch();
    } catch {
      toast.error("Unable to update invite.");
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
          <Show when={(invites()?.length ?? 0) > 0} fallback={<p class="text-base-content/70">No active invites.</p>}>
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
