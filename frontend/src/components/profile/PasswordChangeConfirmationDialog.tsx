import Dialog from "corvu/dialog";

type PasswordChangeConfirmationDialogProps = {
  open: boolean;
  saving: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
};

/** Presents the security consequences of changing a password before confirmation. */
export const PasswordChangeConfirmationDialog = (props: PasswordChangeConfirmationDialogProps) => (
  <Dialog open={props.open} onOpenChange={props.onOpenChange}>
    <Dialog.Portal>
      <Dialog.Overlay class="fixed inset-0 z-50 bg-black/45 data-closed:pointer-events-none" />
      <Dialog.Content class="fixed inset-0 z-[60] m-auto flex h-fit w-[min(92vw,28rem)] flex-col gap-4 rounded-xl border border-base-300 bg-base-100 p-5 shadow-2xl data-closed:pointer-events-none">
        <div class="space-y-1">
          <Dialog.Label class="text-lg font-semibold">Change password and sign out?</Dialog.Label>
          <Dialog.Description class="text-sm text-base-content/70">
            Changing your password immediately signs you out and invalidates every active session on every device. You will need to sign in again with your new password.
          </Dialog.Description>
        </div>
        <div class="flex justify-end gap-2">
          <Dialog.Close class="btn btn-ghost" disabled={props.saving}>
            Cancel
          </Dialog.Close>
          <button
            type="button"
            class="btn btn-primary"
            disabled={props.saving}
            onClick={props.onConfirm}
          >
            Change password and sign out
          </button>
        </div>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog>
);

export default PasswordChangeConfirmationDialog;
