import {action, useAction, useSubmission} from "@solidjs/router";
import {For, Show, createEffect, createResource, createSignal} from "solid-js";
import {toast} from "solid-toast";
import {useApiHost} from "../../../context/ApiHostContext";
import {useProfile} from "../../../context/ProfileContext";
import {ActionResult, AccountRole, ManagedUserRole, ManagedUserRolePage} from "../../../types/Types";
import {fetchManagedUsers, updateManagedUserRole} from "../../../util/common/adminApi";

const PAGE_SIZE = 20;

type UpdateManagedUserRoleActionResult = ActionResult & {
  updatedUser?: ManagedUserRole;
};

export const DashboardManagementPanel = () => {
  const host = useApiHost();
  const profileContext = useProfile();
  const [page, setPage] = createSignal(0);
  const [roleDrafts, setRoleDrafts] = createSignal<Record<number, AccountRole>>({});

  const fetchUsersPage = async (currentPage: number): Promise<ManagedUserRolePage> =>
    fetchManagedUsers(host, currentPage, PAGE_SIZE);

  const [userPage, {refetch, mutate}] = createResource(page, fetchUsersPage);

  createEffect(() => {
    const currentPage = userPage();
    if (!currentPage) {
      return;
    }
    const nextDrafts: Record<number, AccountRole> = {};
    for (const user of currentPage.users) {
      nextDrafts[user.id] = user.role;
    }
    setRoleDrafts(nextDrafts);
  });

  const updateUserRoleAction = action(async (
    userId: number,
    role: AccountRole
  ): Promise<UpdateManagedUserRoleActionResult> => {
    try {
      return {
        ok: true,
        updatedUser: await updateManagedUserRole(host, userId, role)
      };
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : "Unable to update user role."
      };
    }
  }, "updateManagedUserRole");

  const submitUserRoleUpdate = useAction(updateUserRoleAction);
  const updateUserRoleSubmission = useSubmission(updateUserRoleAction);

  createEffect(() => {
    const result = updateUserRoleSubmission.result;
    if (!result) {
      return;
    }

    if (result.ok && result.updatedUser) {
      mutate((current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          users: current.users.map((user) => (
            user.id === result.updatedUser!.id ? result.updatedUser! : user
          ))
        };
      });
      setRoleDrafts((current) => ({
        ...current,
        [result.updatedUser.id]: result.updatedUser.role
      }));
      toast.success("User role updated.");
    } else {
      toast.error(result.message ?? "Unable to update user role.");
    }

    updateUserRoleSubmission.clear();
  });

  const updateDraftRole = (userId: number, nextRole: AccountRole) => {
    setRoleDrafts((current) => ({
      ...current,
      [userId]: nextRole
    }));
  };

  const draftRoleForUser = (user: ManagedUserRole): AccountRole =>
    roleDrafts()[user.id] ?? user.role;

  const isCurrentUser = (user: ManagedUserRole): boolean =>
    user.email.toLowerCase() === (profileContext.profile()?.email ?? "").toLowerCase();

  const isUpdatingUser = (userId: number): boolean =>
    updateUserRoleSubmission.pending && updateUserRoleSubmission.input[0] === userId;

  const canSubmitRoleChange = (user: ManagedUserRole): boolean =>
    !isCurrentUser(user)
      && !isUpdatingUser(user.id)
      && draftRoleForUser(user) !== user.role;

  const applyRoleChange = (user: ManagedUserRole) => {
    const nextRole = draftRoleForUser(user);
    if (!canSubmitRoleChange(user)) {
      return;
    }
    void submitUserRoleUpdate(user.id, nextRole);
  };

  const goToPreviousPage = () => {
    if (page() <= 0 || userPage.loading) {
      return;
    }
    setPage((current) => current - 1);
  };

  const goToNextPage = () => {
    const currentPage = userPage();
    if (!currentPage || userPage.loading || currentPage.page >= currentPage.totalPages - 1) {
      return;
    }
    setPage((current) => current + 1);
  };

  const pageRangeLabel = () => {
    const currentPage = userPage();
    if (!currentPage || currentPage.totalElements === 0) {
      return "No users";
    }
    const start = currentPage.page * currentPage.size + 1;
    const end = Math.min(currentPage.totalElements, start + currentPage.users.length - 1);
    return "Showing " + start + "-" + end + " of " + currentPage.totalElements;
  };

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">User role management</h1>
        <p class="text-base-content/70">
          Review every account and update its access level.
        </p>
      </header>

      <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="font-medium">Accounts</p>
            <p class="text-sm text-base-content/70">{pageRangeLabel()}</p>
          </div>
          <div class="flex gap-2">
            <button type="button" class="btn btn-sm btn-outline" disabled={page() <= 0 || userPage.loading} onClick={goToPreviousPage}>
              Previous
            </button>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              disabled={userPage.loading || !userPage() || userPage()!.page >= userPage()!.totalPages - 1}
              onClick={goToNextPage}
            >
              Next
            </button>
          </div>
        </div>

        <Show when={!userPage.loading} fallback={<p class="text-base-content/70">Loading users...</p>}>
          <Show when={!userPage.error} fallback={
            <div class="space-y-3">
              <p class="text-error">Unable to load users.</p>
              <button type="button" class="btn btn-outline" onClick={() => void refetch()}>
                Retry
              </button>
            </div>
          }>
            <Show when={(userPage()?.users.length ?? 0) > 0} fallback={
              <p class="text-base-content/70">No users are available.</p>
            }>
              <ul class="space-y-3">
                <For each={userPage()?.users}>
                  {(user) => (
                    <li class="rounded-xl border border-base-300 bg-base-200/40 p-4">
                      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                        <div class="space-y-1">
                          <p class="font-semibold">{user.name || "Unnamed user"}</p>
                          <p class="text-sm text-base-content/70">{user.email}</p>
                          <p class="text-xs uppercase tracking-wide text-base-content/60">
                            Current role: {user.role}
                          </p>
                        </div>
                        <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
                          <select
                            class="select select-bordered select-sm min-w-40"
                            value={draftRoleForUser(user)}
                            disabled={isCurrentUser(user) || updateUserRoleSubmission.pending}
                            onChange={(event) => updateDraftRole(user.id, event.currentTarget.value as AccountRole)}
                          >
                            <option value="client">client</option>
                            <option value="partner">partner</option>
                            <option value="admin">admin</option>
                          </select>
                          <button
                            type="button"
                            class="btn btn-sm btn-primary"
                            disabled={!canSubmitRoleChange(user)}
                            onClick={() => applyRoleChange(user)}
                          >
                            {isUpdatingUser(user.id) ? "Updating..." : "Update role"}
                          </button>
                        </div>
                      </div>
                      <Show when={isCurrentUser(user)}>
                        <p class="mt-2 text-xs text-base-content/60">
                          Your own account role cannot be changed from this panel.
                        </p>
                      </Show>
                    </li>
                  )}
                </For>
              </ul>
            </Show>
          </Show>
        </Show>
      </section>
    </div>
  );
};
