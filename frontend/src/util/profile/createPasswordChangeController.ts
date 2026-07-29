import {createSignal} from "solid-js";
import type {PasswordChangeCredentials} from "./profilePasswordApi";

type PasswordChangeControllerDependencies = {
  updatePassword: (credentials: PasswordChangeCredentials) => Promise<void>;
  notifySuccess: () => void;
  notifyError: (message: string) => void;
  redirectToLogin: () => void;
};

/** Owns password-change state and confirmation workflow independently of presentation. */
export const createPasswordChangeController = (dependencies: PasswordChangeControllerDependencies) => {
  const [currentPassword, setCurrentPassword] = createSignal("");
  const [newPassword, setNewPassword] = createSignal("");
  const [confirmationOpen, setConfirmationOpen] = createSignal(false);
  const [saving, setSaving] = createSignal(false);

  const requestConfirmation = () => {
    if (!saving()) {
      setConfirmationOpen(true);
    }
  };

  const closeConfirmation = () => {
    if (!saving()) {
      setConfirmationOpen(false);
    }
  };

  const confirm = async (): Promise<void> => {
    if (saving() || !confirmationOpen()) {
      return;
    }

    setConfirmationOpen(false);
    setSaving(true);
    try {
      try {
        await dependencies.updatePassword({
          currentPassword: currentPassword().trim(),
          newPassword: newPassword().trim()
        });
      } catch (error) {
        dependencies.notifyError(error instanceof Error ? error.message : "Unable to update password");
        return;
      }

      setCurrentPassword("");
      setNewPassword("");
      try {
        dependencies.notifySuccess();
      } catch {
        // Notification rendering must not strand a user whose sessions are now revoked.
      }
      dependencies.redirectToLogin();
    } finally {
      setSaving(false);
    }
  };

  return {
    closeConfirmation,
    confirmationOpen,
    confirm,
    currentPassword,
    newPassword,
    requestConfirmation,
    saving,
    setCurrentPassword,
    setNewPassword
  };
};

export type PasswordChangeController = ReturnType<typeof createPasswordChangeController>;
