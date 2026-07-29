import {createRoot} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import {createPasswordChangeController} from "../util/profile/createPasswordChangeController";

const controllerFixture = (updatePassword = vi.fn(async () => undefined)) => {
  const notifySuccess = vi.fn();
  const notifyError = vi.fn();
  const redirectToLogin = vi.fn();
  const controller = createPasswordChangeController({
    updatePassword,
    notifySuccess,
    notifyError,
    redirectToLogin
  });
  return {controller, notifyError, notifySuccess, redirectToLogin, updatePassword};
};

describe("password change controller", () => {
  it("opens confirmation without submitting credentials", () => createRoot((dispose) => {
    const fixture = controllerFixture();

    fixture.controller.requestConfirmation();

    expect(fixture.controller.confirmationOpen()).toBe(true);
    expect(fixture.updatePassword).not.toHaveBeenCalled();
    dispose();
  }));

  it("cancels without submitting credentials", () => createRoot((dispose) => {
    const fixture = controllerFixture();
    fixture.controller.requestConfirmation();

    fixture.controller.closeConfirmation();

    expect(fixture.controller.confirmationOpen()).toBe(false);
    expect(fixture.updatePassword).not.toHaveBeenCalled();
    dispose();
  }));

  it("submits once, clears credentials, and redirects after success", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const fixture = controllerFixture();
      fixture.controller.setCurrentPassword("old-password");
      fixture.controller.setNewPassword("new-password");
      fixture.controller.requestConfirmation();

      Promise.all([fixture.controller.confirm(), fixture.controller.confirm()]).then(() => {
        try {
          expect(fixture.updatePassword).toHaveBeenCalledOnce();
          expect(fixture.updatePassword).toHaveBeenCalledWith({
            currentPassword: "old-password",
            newPassword: "new-password"
          });
          expect(fixture.controller.currentPassword()).toBe("");
          expect(fixture.controller.newPassword()).toBe("");
          expect(fixture.notifySuccess).toHaveBeenCalledOnce();
          expect(fixture.redirectToLogin).toHaveBeenCalledOnce();
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });

  it("preserves credentials and reports an unsuccessful change", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const fixture = controllerFixture(vi.fn(async () => {
        throw new Error("Current password is incorrect");
      }));
      fixture.controller.setCurrentPassword("wrong-password");
      fixture.controller.setNewPassword("new-password");
      fixture.controller.requestConfirmation();

      fixture.controller.confirm().then(() => {
        try {
          expect(fixture.controller.currentPassword()).toBe("wrong-password");
          expect(fixture.controller.newPassword()).toBe("new-password");
          expect(fixture.notifyError).toHaveBeenCalledWith("Current password is incorrect");
          expect(fixture.redirectToLogin).not.toHaveBeenCalled();
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });

  it("redirects without reporting request failure when success notification throws", async () => {
    await new Promise<void>((resolve, reject) => createRoot((dispose) => {
      const fixture = controllerFixture();
      fixture.notifySuccess.mockImplementation(() => {
        throw new Error("toast renderer unavailable");
      });
      fixture.controller.setCurrentPassword("old-password");
      fixture.controller.setNewPassword("new-password");
      fixture.controller.requestConfirmation();

      fixture.controller.confirm().then(() => {
        try {
          expect(fixture.updatePassword).toHaveBeenCalledOnce();
          expect(fixture.notifyError).not.toHaveBeenCalled();
          expect(fixture.redirectToLogin).toHaveBeenCalledOnce();
          resolve();
        } catch (error) {
          reject(error);
        } finally {
          dispose();
        }
      });
    }));
  });
});
