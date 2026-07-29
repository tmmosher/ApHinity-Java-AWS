import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("corvu/dialog", () => {
  const Part = (props: {children?: unknown}) => <>{props.children as never}</>;
  const Dialog = Part as typeof Part & Record<string, typeof Part>;
  Dialog.Portal = Part;
  Dialog.Overlay = Part;
  Dialog.Content = Part;
  Dialog.Label = Part;
  Dialog.Description = Part;
  Dialog.Close = Part;
  return {default: Dialog};
});

import PasswordChangeConfirmationDialog from "../components/profile/PasswordChangeConfirmationDialog";
import PasswordChangeForm from "../components/profile/PasswordChangeForm";
import {createPasswordChangeController} from "../util/profile/createPasswordChangeController";

describe("password change presentation", () => {
  it("explains session invalidation before confirmation", () => {
    const html = renderToString(() => (
      <PasswordChangeConfirmationDialog
        open
        saving={false}
        onOpenChange={vi.fn()}
        onConfirm={vi.fn()}
      />
    ));

    expect(html).toContain("Change password and sign out?");
    expect(html).toContain("invalidates every active session on every device");
    expect(html).toContain("Change password and sign out");
  });

  it("renders required password-manager-compatible inputs", () => {
    const controller = createPasswordChangeController({
      updatePassword: vi.fn(async () => undefined),
      notifySuccess: vi.fn(),
      notifyError: vi.fn(),
      redirectToLogin: vi.fn()
    });

    const html = renderToString(() => <PasswordChangeForm controller={controller} />);

    expect(html).toContain("autocomplete=\"current-password\"");
    expect(html).toContain("autocomplete=\"new-password\"");
    expect(html.match(/required/g)).toHaveLength(2);
  });
});
