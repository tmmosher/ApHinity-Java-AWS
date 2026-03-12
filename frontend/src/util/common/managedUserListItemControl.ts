import {createSignal} from "solid-js";
import {AccountRole, ManagedUser} from "../../types/Types";

export type ManagedUserMutationMode = "delete" | "restore" | "role" | null;

export const createManagedUserListItemControl = (user: ManagedUser) => {
  const [pendingDeletion, setPendingDeletion] = createSignal(user.pendingDeletion);
  const [roleDraft, setRoleDraft] = createSignal<AccountRole>(user.role);
  const [isMutating, setIsMutating] = createSignal(false);
  const [mutationMode, setMutationMode] = createSignal<ManagedUserMutationMode>(null);

  const syncUser = (nextUser: ManagedUser) => {
    setPendingDeletion(nextUser.pendingDeletion);
    setRoleDraft(nextUser.role);
    setIsMutating(false);
    setMutationMode(null);
  };

  const toggleDeletionState = async (
    userId: number,
    onMarkForDeletion: (targetUserId: number) => Promise<ManagedUser>,
    onRestoreDeletion: (targetUserId: number) => Promise<ManagedUser>
  ) => {
    if (isMutating()) {
      return null;
    }

    const nextPendingState = !pendingDeletion();
    setPendingDeletion(nextPendingState);
    setIsMutating(true);
    setMutationMode(nextPendingState ? "delete" : "restore");

    try {
      const updatedUser = nextPendingState
        ? await onMarkForDeletion(userId)
        : await onRestoreDeletion(userId);
      setPendingDeletion(updatedUser.pendingDeletion);
      setRoleDraft(updatedUser.role);
      return updatedUser;
    } catch (error) {
      setPendingDeletion(!nextPendingState);
      throw error;
    } finally {
      setIsMutating(false);
      setMutationMode(null);
    }
  };

  const applyRoleChange = async (
    userId: number,
    persistedRole: AccountRole,
    onUpdateRole: (targetUserId: number, role: AccountRole) => Promise<ManagedUser>
  ) => {
    if (isMutating() || roleDraft() === persistedRole) {
      return null;
    }

    const previousRole = persistedRole;
    const nextRole = roleDraft();
    setIsMutating(true);
    setMutationMode("role");

    try {
      const updatedUser = await onUpdateRole(userId, nextRole);
      setRoleDraft(updatedUser.role);
      setPendingDeletion(updatedUser.pendingDeletion);
      return updatedUser;
    } catch (error) {
      setRoleDraft(previousRole);
      throw error;
    } finally {
      setIsMutating(false);
      setMutationMode(null);
    }
  };

  return {
    pendingDeletion,
    roleDraft,
    setRoleDraft,
    isBusy: isMutating,
    mutationMode,
    syncUser,
    toggleDeletionState,
    applyRoleChange
  };
};
