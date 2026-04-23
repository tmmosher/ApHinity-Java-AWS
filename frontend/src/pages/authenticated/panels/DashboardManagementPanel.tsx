import {For, Show, createResource} from "solid-js";
import {toast} from "solid-toast";
import {AccountRole, ManagedUser, ManagedUserPage} from "../../../types/Types";
import {
  fetchManagedUsers,
  updateManagedUserRole,
  markManagedUserForDeletion,
  restoreManagedUserDeletion
} from "../../../util/common/adminApi";
import {useApiHost} from "../../../context/ApiHostContext";
import {ManagedUserListItem} from "../../../components/management/ManagedUserListItem";
import {
  createManagedUserSearchControl,
  getManagedUserEmptyStateMessage,
  getManagedUserPageRangeLabel
} from "../../../util/common/managedUserSearchControl";

const PAGE_SIZE = 12;

export const DashboardManagementPanel = () => {
  const host = useApiHost();
  const searchControl = createManagedUserSearchControl();

  const fetchUsersPage = async (
    key: {page: number; query: string}
  ): Promise<ManagedUserPage> => fetchManagedUsers(host, key.page, PAGE_SIZE, key.query);

  const [userPage, {refetch, mutate}] = createResource(
    () => ({
      page: searchControl.page(),
      query: searchControl.searchQuery()
    }),
    fetchUsersPage
  );

  const updateManagedUserInPage = (updatedUser: ManagedUser) => {
    mutate((current) => {
      if (!current) {
        return current;
      }
      return {
        ...current,
        users: current.users.map((user) => (
          user.id === updatedUser.id ? updatedUser : user
        ))
      };
    });
  };

  const applyManagedUserRoleChange = async (
    userId: number,
    role: AccountRole
  ): Promise<ManagedUser> => {
    const updatedUser = await updateManagedUserRole(host, userId, role);
    if (updatedUser.role === "admin") {
      toast.success("User role updated");
      void refetch();
      return updatedUser;
    }

    updateManagedUserInPage(updatedUser);
    toast.success("User role updated");
    return updatedUser;
  };

  const queueUserDeletion = async (userId: number): Promise<ManagedUser> => {
    const updatedUser = await markManagedUserForDeletion(host, userId);
    updateManagedUserInPage(updatedUser);
    toast.success("User queued for deletion");
    return updatedUser;
  };

  const restoreUserFromDeletionQueue = async (userId: number): Promise<ManagedUser> => {
    const updatedUser = await restoreManagedUserDeletion(host, userId);
    updateManagedUserInPage(updatedUser);
    toast.success("User restored");
    return updatedUser;
  };

  const goToPreviousPage = () => {
    if (searchControl.page() <= 0 || userPage.loading) {
      return;
    }
    searchControl.setPage((current) => current - 1);
  };

  const goToNextPage = () => {
    const currentPage = userPage();
    if (!currentPage || userPage.loading || currentPage.page >= currentPage.totalPages - 1) {
      return;
    }
    searchControl.setPage((current) => current + 1);
  };

  const pageRangeLabel = () => getManagedUserPageRangeLabel(userPage(), searchControl.searchQuery());
  const emptyStateMessage = () => getManagedUserEmptyStateMessage(searchControl.searchQuery());

  return (
    <div class="space-y-6">
      <header class="space-y-1">
        <h1 class="text-3xl font-semibold tracking-tight">User Management</h1>
        <p class="text-base-content/70">
          Search accounts, update their role, and queue them for deletion.
        </p>
      </header>

      <section class="space-y-4 rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div class="space-y-1">
            <p class="font-medium">Accounts</p>
            <p class="text-sm text-base-content/70">{pageRangeLabel()}</p>
          </div>
          <label class="form-control w-full max-w-md">
            <span class="label-text text-sm">Search by email</span>
            <input
              type="search"
              class="input input-bordered w-full"
              value={searchControl.searchDraft()}
              placeholder="Search by email substring"
              onInput={(event) => searchControl.updateSearchDraft(event.currentTarget.value)}
            />
          </label>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex gap-2">
            <button
              type="button"
              class="btn btn-sm btn-outline"
              disabled={searchControl.page() <= 0 || userPage.loading}
              onClick={goToPreviousPage}
            >
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
              <p class="text-base-content/70">{emptyStateMessage()}</p>
            }>
              <ul class="space-y-3">
                <For each={userPage()?.users}>
                  {(user) => (
                    <ManagedUserListItem
                      user={user}
                      onUpdateRole={applyManagedUserRoleChange}
                      onMarkForDeletion={queueUserDeletion}
                      onRestoreDeletion={restoreUserFromDeletionQueue}
                    />
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
