import type {PasswordChangeController} from "../../util/profile/createPasswordChangeController";

type PasswordChangeFormProps = {
  controller: PasswordChangeController;
};

/** Presents credential inputs and requests confirmation without submitting credentials. */
export const PasswordChangeForm = (props: PasswordChangeFormProps) => (
  <form
    class="mt-4 grid gap-4"
    onSubmit={(event) => {
      event.preventDefault();
      props.controller.requestConfirmation();
    }}
  >
    <label class="form-control">
      <span class="label-text">Current password</span>
      <input
        type="password"
        name="currentPassword"
        autocomplete="current-password"
        class="input input-bordered mt-1"
        required
        value={props.controller.currentPassword()}
        onInput={(event) => props.controller.setCurrentPassword(event.currentTarget.value)}
      />
    </label>
    <label class="form-control">
      <span class="label-text">New password</span>
      <input
        type="password"
        name="newPassword"
        autocomplete="new-password"
        class="input input-bordered mt-1"
        required
        value={props.controller.newPassword()}
        onInput={(event) => props.controller.setNewPassword(event.currentTarget.value)}
      />
    </label>
    <button type="submit" class="btn btn-primary w-fit" disabled={props.controller.saving()}>
      {props.controller.saving() ? "Saving..." : "Update password"}
    </button>
  </form>
);

export default PasswordChangeForm;
