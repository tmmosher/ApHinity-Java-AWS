import { createSignal, onCleanup, type Accessor } from "solid-js";
import { ActionResult } from "../types/Types";

export const RECOVERY_SUBMIT_COOLDOWN_MS = 10_000;
export const RECOVERY_SUBMIT_COOLDOWN_SECONDS = RECOVERY_SUBMIT_COOLDOWN_MS / 1_000;
export const RECOVERY_COOLDOWN_MESSAGE =
  `Please wait ${RECOVERY_SUBMIT_COOLDOWN_SECONDS} seconds before requesting another email.`;

const RECOVERY_SUBMIT_ENABLED_CLASS = "btn btn-primary w-full text-center";
const RECOVERY_SUBMIT_DISABLED_CLASS =
  "btn w-full text-center bg-gray-400 border-gray-400 text-gray-100 hover:bg-gray-400 cursor-not-allowed";

export type RecoverySubmissionControl = {
  recoveryCooldownActive: Accessor<boolean>;
  turnstileInstance: Accessor<number>;
  startRecoveryCooldown: () => void;
  resetRecoveryCaptcha: () => void;
};

export const createRecoverySubmissionControl = (
  cooldownMs = RECOVERY_SUBMIT_COOLDOWN_MS
): RecoverySubmissionControl => {
  const [recoveryCooldownActive, setRecoveryCooldownActive] = createSignal(false);
  const [turnstileInstance, setTurnstileInstance] = createSignal(1);
  let recoveryCooldownTimer: ReturnType<typeof setTimeout> | undefined;

  const startRecoveryCooldown = () => {
    setRecoveryCooldownActive(true);
    if (recoveryCooldownTimer) {
      clearTimeout(recoveryCooldownTimer);
    }
    recoveryCooldownTimer = setTimeout(() => {
      setRecoveryCooldownActive(false);
      recoveryCooldownTimer = undefined;
    }, cooldownMs);
  };

  const resetRecoveryCaptcha = () => {
    setTurnstileInstance((previous) => previous + 1);
  };

  onCleanup(() => {
    if (recoveryCooldownTimer) {
      clearTimeout(recoveryCooldownTimer);
    }
  });

  return {
    recoveryCooldownActive,
    turnstileInstance,
    startRecoveryCooldown,
    resetRecoveryCaptcha
  };
};

export const getRecoveryCooldownActionResult = (): ActionResult => ({
  ok: false,
  code: "recovery_cooldown",
  message: RECOVERY_COOLDOWN_MESSAGE
});

export const isRecoverySubmitDisabled = (
  recoveryCooldownActive: boolean,
  recoverySubmissionPending: boolean
): boolean => recoveryCooldownActive || recoverySubmissionPending;

export const getRecoverySubmitButtonClass = (disabled: boolean): string =>
  disabled ? RECOVERY_SUBMIT_DISABLED_CLASS : RECOVERY_SUBMIT_ENABLED_CLASS;
