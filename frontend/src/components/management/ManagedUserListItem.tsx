import {createEffect} from "solid-js";
import {toast} from "solid-toast";
import {AccountRole, ManagedUser} from "../../types/Types";
import {createManagedUserListItemControl} from "../../util/common/managedUserListItemControl";

type ManagedUserListItemProps = {
  user: ManagedUser;
  onUpdateRole: (userId: number, role: AccountRole) => Promise<ManagedUser>;
  onMarkForDeletion: (userId: number) => Promise<ManagedUser>;
  onRestoreDeletion: (userId: number) => Promise<ManagedUser>;
};

export const ManagedUserListItem = (props: ManagedUserListItemProps) => {
  const control = createManagedUserListItemControl(props.user);

  createEffect(() => {
    void props.user.id;
    void props.user.role;
    void props.user.pendingDeletion;
    control.syncUser(props.user);
  });

  const toggleDeletionState = async () => {
    try {
      await control.toggleDeletionState(
        props.user.id,
        props.onMarkForDeletion,
        props.onRestoreDeletion
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update user deletion state.");
    }
  };

  const applyRoleChange = async () => {
    try {
      await control.applyRoleChange(
        props.user.id,
        props.user.role,
        props.onUpdateRole
      );
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update user role.");
    }
  };

  return (
    <li
      class={
        "rounded-xl border border-base-300 bg-base-200/40 p-4 transition-opacity duration-200 "
        + (control.pendingDeletion() ? "opacity-60" : "opacity-100")
      }
    >
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div class="space-y-1">
          <p class="text-sm font-medium text-base-content">{props.user.email}</p>
          <p class="text-sm text-base-content/70">
            {(props.user.name || "Unnamed user") + " / " + props.user.role}
          </p>
        </div>
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
          <select
            class="select select-bordered select-sm min-w-32"
            value={control.roleDraft()}
            disabled={control.isBusy()}
            onChange={(event) => control.setRoleDraft(event.currentTarget.value as AccountRole)}
          >
            <option value="client">client</option>
            <option value="partner">partner</option>
            <option value="admin">admin</option>
          </select>
          <button
            type="button"
            class="btn btn-primary btn-sm min-w-24"
            disabled={control.isBusy() || control.roleDraft() === props.user.role}
            onClick={() => void applyRoleChange()}
          >
            {control.mutationMode() === "role" ? "Updating..." : "Update role"}
          </button>
          <button
            type="button"
            class={"btn btn-sm min-w-24 " + (control.pendingDeletion() ? "btn-outline" : "btn-error")}
            disabled={control.isBusy()}
            onClick={() => void toggleDeletionState()}
          >
            {control.mutationMode() === "delete"
              ? "Deleting..."
              : control.mutationMode() === "restore"
                ? "Restoring..."
                : (control.pendingDeletion() ? "Restore" : "Delete")}
          </button>
        </div>
      </div>
    </li>
  );
};
