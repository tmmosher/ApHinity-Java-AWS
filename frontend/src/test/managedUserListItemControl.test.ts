import {createRoot} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import {ManagedUser} from "../types/Types";
import {createManagedUserListItemControl} from "../util/common/managedUserListItemControl";

const managedUser = (overrides: Partial<ManagedUser> = {}): ManagedUser => ({
  id: 7,
  name: "Client User",
  email: "client@example.com",
  role: "client",
  pendingDeletion: false,
  ...overrides
});

const deferred = <T>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((innerResolve, innerReject) => {
    resolve = innerResolve;
    reject = innerReject;
  });
  return {promise, resolve, reject};
};

describe("managedUserListItemControl", () => {
  it("optimistically marks a user for deletion and settles with the server response", async () => {
    await createRoot(async (dispose) => {
      const control = createManagedUserListItemControl(managedUser());
      const pendingRequest = deferred<ManagedUser>();
      const onMarkForDeletion = vi.fn().mockReturnValue(pendingRequest.promise);
      const onRestoreDeletion = vi.fn();

      const mutationPromise = control.toggleDeletionState(7, onMarkForDeletion, onRestoreDeletion);

      expect(control.pendingDeletion()).toBe(true);
      expect(control.isBusy()).toBe(true);
      expect(control.mutationMode()).toBe("delete");

      pendingRequest.resolve(managedUser({pendingDeletion: true}));
      await mutationPromise;

      expect(control.pendingDeletion()).toBe(true);
      expect(control.isBusy()).toBe(false);
      expect(control.mutationMode()).toBeNull();

      dispose();
    });
  });

  it("reverts optimistic deletion state when the deletion call fails", async () => {
    await createRoot(async (dispose) => {
      const control = createManagedUserListItemControl(managedUser());
      const onMarkForDeletion = vi.fn().mockRejectedValue(new Error("queue full"));

      await expect(
        control.toggleDeletionState(7, onMarkForDeletion, vi.fn())
      ).rejects.toThrowError("queue full");

      expect(control.pendingDeletion()).toBe(false);
      expect(control.isBusy()).toBe(false);
      expect(control.mutationMode()).toBeNull();

      dispose();
    });
  });

  it("updates the role draft when a role change succeeds and no-ops when unchanged", async () => {
    await createRoot(async (dispose) => {
      const control = createManagedUserListItemControl(managedUser());
      const onUpdateRole = vi.fn().mockResolvedValue(managedUser({role: "partner"}));

      expect(await control.applyRoleChange(7, "client", onUpdateRole)).toBeNull();
      expect(onUpdateRole).not.toHaveBeenCalled();

      control.setRoleDraft("partner");
      await control.applyRoleChange(7, "client", onUpdateRole);

      expect(onUpdateRole).toHaveBeenCalledWith(7, "partner");
      expect(control.roleDraft()).toBe("partner");
      expect(control.pendingDeletion()).toBe(false);

      dispose();
    });
  });
});
