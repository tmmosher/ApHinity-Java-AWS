import {useNavigate} from "@solidjs/router";
import {toast} from "solid-toast";
import {useApiHost} from "../../context/ApiHostContext";
import {createPasswordChangeController} from "../../util/profile/createPasswordChangeController";
import {updateProfilePassword} from "../../util/profile/profilePasswordApi";
import PasswordChangeConfirmationDialog from "./PasswordChangeConfirmationDialog";
import PasswordChangeForm from "./PasswordChangeForm";

/** Composes the password-change workflow with API, notification, and navigation dependencies. */
export const PasswordChangeSection = () => {
  const apiHost = useApiHost();
  const navigate = useNavigate();
  const controller = createPasswordChangeController({
    updatePassword: (credentials) => updateProfilePassword(apiHost, credentials),
    notifySuccess: () => toast.success("Password updated. Please sign in again"),
    notifyError: (message) => toast.error(message),
    redirectToLogin: () => navigate("/login", {replace: true})
  });

  return (
    <section class="rounded-xl border border-base-300 bg-base-100 p-5 shadow-sm">
      <h2 class="text-lg font-semibold">Password</h2>
      <p class="mt-1 text-sm text-base-content/70">Set a new password for this account.</p>
      <PasswordChangeForm controller={controller} />
      <PasswordChangeConfirmationDialog
        open={controller.confirmationOpen()}
        saving={controller.saving()}
        onOpenChange={(open) => {
          if (!open) {
            controller.closeConfirmation();
          }
        }}
        onConfirm={() => void controller.confirm()}
      />
    </section>
  );
};

export default PasswordChangeSection;
