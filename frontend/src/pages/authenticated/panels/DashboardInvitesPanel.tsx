import {action, useAction, useSubmission} from "@solidjs/router";
import {For, Show, createEffect, createResource} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {ActionResult, ActiveInvite} from "../../../types/Types";
import {fetchActiveInvites, processLocationInvite} from "../../../util/common/inviteApi";

type ProcessInviteActionResult = ActionResult & {
  inviteId: number;
  decision: "accept" | "decline";
};

export const DashboardInvitesPanel = () => {
  const host = useApiHost();

  /**
   * Retrieves pending invites for the signed-in user.
   *
   * Endpoint: `GET /api/core/location-invites/active`
   */
  const fetchInvites = async (): Promise<ActiveInvite[]> =>
    fetchActiveInvites(host);

  const [invites, {refetch}] = createResource(fetchInvites);

  const processInviteAction = action(async (
    inviteId: number,
    decision: "accept" | "decline"
  ): Promise<ProcessInviteActionResult> => {
    try {
      await processLocationInvite(host, inviteId, decision);
      return {
        ok: true,
        inviteId,
        decision
      };
    } catch (error) {
      return {
        ok: false,
        inviteId,
        decision,
        message: error instanceof Error ? error.message : "Unable to update invite."
      };
    }
  }, "processInvite");

  const submitInviteDecision = useAction(processInviteAction);
  const inviteDecisionSubmission = useSubmission(processInviteAction);

  createEffect(() => {
    const result = inviteDecisionSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok) {
      toast.success(result.decision === "accept" ? "Invite accepted." : "Invite declined.");
      void refetch();
    } else {
      toast.error(result.message ?? "Unable to update invite.");
    }

    inviteDecisionSubmission.clear();
  });

  const processingInviteId = () =>
    inviteDecisionSubmission.pending
      ? inviteDecisionSubmission.input[0] as number
      : null;
  const processingDecision = () =>
    inviteDecisionSubmission.pending
      ? inviteDecisionSubmission.input[1] as "accept" | "decline"
      : null;

  /**
   * Accepts or declines a pending invite.
   *
   * Endpoint: `POST /api/core/location-invites/{inviteId}/{action}`
   *
   * @param inviteId Invite identifier.
   * @param action Decision to apply (`accept` or `decline`).
   */
  const processInvite = (inviteId: number, action: "accept" | "decline") => {
    if (inviteDecisionSubmission.pending) {
      return;
    }

    void submitInviteDecision(inviteId, action);
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
                          disabled={inviteDecisionSubmission.pending}
                          onClick={() => void processInvite(invite.id, "accept")}
                        >
                          {processingInviteId() === invite.id && processingDecision() === "accept" ? "Working..." : "Accept"}
                        </button>
                        <button
                          type="button"
                          class="btn btn-sm btn-outline"
                          disabled={inviteDecisionSubmission.pending}
                          onClick={() => void processInvite(invite.id, "decline")}
                        >
                          {processingInviteId() === invite.id && processingDecision() === "decline" ? "Working..." : "Decline"}
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
