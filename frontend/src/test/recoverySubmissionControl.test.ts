import {createRoot} from "solid-js";
import {describe, expect, it, vi} from "vitest";
import {
  createRecoverySubmissionControl,
  getRecoveryCooldownActionResult,
  getRecoverySubmitButtonClass,
  isRecoverySubmitDisabled,
  RECOVERY_COOLDOWN_MESSAGE
} from "../util/common/recoverySubmissionControl";

describe("recoverySubmissionControl", () => {
  it("starts with no cooldown and the initial turnstile instance", () => {
    vi.useFakeTimers();
    try {
      createRoot((dispose) => {
        try {
          const control = createRecoverySubmissionControl();

          expect(control.recoveryCooldownActive()).toBe(false);
          expect(control.turnstileInstance()).toBe(1);
          expect(isRecoverySubmitDisabled(control.recoveryCooldownActive(), false)).toBe(false);
        } finally {
          dispose();
        }
      });
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });

  it("activates cooldown immediately and clears it after the configured duration", () => {
    vi.useFakeTimers();
    try {
      createRoot((dispose) => {
        try {
          const cooldownMs = 100;
          const control = createRecoverySubmissionControl(cooldownMs);

          control.startRecoveryCooldown();
          expect(control.recoveryCooldownActive()).toBe(true);

          vi.advanceTimersByTime(cooldownMs - 1);
          expect(control.recoveryCooldownActive()).toBe(true);

          vi.advanceTimersByTime(1);
          expect(control.recoveryCooldownActive()).toBe(false);
        } finally {
          dispose();
        }
      });
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });

  it("restarts cooldown when submission happens again before timeout", () => {
    vi.useFakeTimers();
    try {
      createRoot((dispose) => {
        try {
          const cooldownMs = 100;
          const control = createRecoverySubmissionControl(cooldownMs);

          control.startRecoveryCooldown();
          vi.advanceTimersByTime(50);
          control.startRecoveryCooldown();

          vi.advanceTimersByTime(cooldownMs - 1);
          expect(control.recoveryCooldownActive()).toBe(true);

          vi.advanceTimersByTime(1);
          expect(control.recoveryCooldownActive()).toBe(false);
        } finally {
          dispose();
        }
      });
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });

  it("increments turnstile instance when captcha is reset", () => {
    vi.useFakeTimers();
    try {
      createRoot((dispose) => {
        try {
          const control = createRecoverySubmissionControl();

          control.resetRecoveryCaptcha();
          control.resetRecoveryCaptcha();

          expect(control.turnstileInstance()).toBe(3);
        } finally {
          dispose();
        }
      });
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
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
