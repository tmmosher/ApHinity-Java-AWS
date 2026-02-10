import { createRoot } from "solid-js";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createRecoverySubmissionControl,
  getRecoveryCooldownActionResult,
  getRecoverySubmitButtonClass,
  isRecoverySubmitDisabled,
  RECOVERY_COOLDOWN_MESSAGE,
  RECOVERY_SUBMIT_COOLDOWN_MS
} from "../util/recoverySubmissionControl";

describe("recoverySubmissionControl", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts with no cooldown and the initial turnstile instance", () => {
    createRoot((dispose) => {
      const control = createRecoverySubmissionControl();

      expect(control.recoveryCooldownActive()).toBe(false);
      expect(control.turnstileInstance()).toBe(1);
      expect(isRecoverySubmitDisabled(control.recoveryCooldownActive(), false)).toBe(false);

      dispose();
    });
  });

  it("activates cooldown immediately and clears it after 10 seconds", () => {
    createRoot((dispose) => {
      const control = createRecoverySubmissionControl(RECOVERY_SUBMIT_COOLDOWN_MS);

      control.startRecoveryCooldown();
      expect(control.recoveryCooldownActive()).toBe(true);

      vi.advanceTimersByTime(RECOVERY_SUBMIT_COOLDOWN_MS - 1);
      expect(control.recoveryCooldownActive()).toBe(true);

      vi.advanceTimersByTime(1);
      expect(control.recoveryCooldownActive()).toBe(false);

      dispose();
    });
  });

  it("restarts cooldown when submission happens again before timeout", () => {
    createRoot((dispose) => {
      const control = createRecoverySubmissionControl(RECOVERY_SUBMIT_COOLDOWN_MS);

      control.startRecoveryCooldown();
      vi.advanceTimersByTime(5_000);
      control.startRecoveryCooldown();

      vi.advanceTimersByTime(RECOVERY_SUBMIT_COOLDOWN_MS - 1);
      expect(control.recoveryCooldownActive()).toBe(true);

      vi.advanceTimersByTime(1);
      expect(control.recoveryCooldownActive()).toBe(false);

      dispose();
    });
  });

  it("increments turnstile instance when captcha is reset", () => {
    createRoot((dispose) => {
      const control = createRecoverySubmissionControl();

      control.resetRecoveryCaptcha();
      control.resetRecoveryCaptcha();

      expect(control.turnstileInstance()).toBe(3);

      dispose();
    });
  });

  it("returns expected cooldown action payload", () => {
    expect(getRecoveryCooldownActionResult()).toEqual({
      ok: false,
      code: "recovery_cooldown",
      message: RECOVERY_COOLDOWN_MESSAGE
    });
  });

  it("computes disabled state and button styles for cooldown state", () => {
    expect(isRecoverySubmitDisabled(false, false)).toBe(false);
    expect(isRecoverySubmitDisabled(true, false)).toBe(true);
    expect(isRecoverySubmitDisabled(false, true)).toBe(true);

    expect(getRecoverySubmitButtonClass(false)).toContain("btn-primary");
    expect(getRecoverySubmitButtonClass(true)).toContain("bg-gray-400");
    expect(getRecoverySubmitButtonClass(true)).toContain("cursor-not-allowed");
  });
});
