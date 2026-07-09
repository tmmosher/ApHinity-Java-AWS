import {createSignal, onCleanup, onMount} from "solid-js";
import {toast} from "solid-toast";

const TURNSTILE_SCRIPT_ID = "aphinity-turnstile-script";
const TURNSTILE_SCRIPT_SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js";
const TURNSTILE_RESPONSE_FIELD = "cf-turnstile-response";
const siteKey = (import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined)?.trim();

let turnstileScriptPromise: Promise<void> | null = null;
let turnstileWidgetSequence = 0;
const turnstileCallbacks = new Map<string, TurnstileCallbackHandlers>();

type TurnstileCallbackHandlers = {
  token: (token: string) => void;
  error: (errorCode?: string) => void;
  clear: () => void;
};

declare global {
  interface Window {
    aphinityTurnstileCallback?: (token: string) => void;
    aphinityTurnstileErrorCallback?: (errorCode?: string) => void;
    aphinityTurnstileExpiredCallback?: () => void;
    aphinityTurnstileTimeoutCallback?: () => void;
  }
}

const activeWidgetId = (): string | undefined =>
  document.querySelector<HTMLElement>(".cf-turnstile[data-aphinity-turnstile-id]")?.dataset.aphinityTurnstileId;

const ensureTurnstileGlobalCallbacks = (): void => {
  if (typeof window === "undefined") {
    return;
  }

  window.aphinityTurnstileCallback = (token: string) => {
    const widgetId = activeWidgetId();
    if (!widgetId) {
      return;
    }
    turnstileCallbacks.get(widgetId)?.token(token);
  };
  window.aphinityTurnstileErrorCallback = (errorCode?: string) => {
    const widgetId = activeWidgetId();
    if (!widgetId) {
      return;
    }
    turnstileCallbacks.get(widgetId)?.error(errorCode);
  };
  window.aphinityTurnstileExpiredCallback = () => {
    const widgetId = activeWidgetId();
    if (!widgetId) {
      return;
    }
    turnstileCallbacks.get(widgetId)?.clear();
  };
  window.aphinityTurnstileTimeoutCallback = () => {
    const widgetId = activeWidgetId();
    if (!widgetId) {
      return;
    }
    turnstileCallbacks.get(widgetId)?.clear();
  };
};

const loadTurnstileScript = (): Promise<void> => {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Turnstile requires a browser environment"));
  }
  if (turnstileScriptPromise) {
    return turnstileScriptPromise;
  }

  ensureTurnstileGlobalCallbacks();
  turnstileScriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null;
    if (existingScript) {
      existingScript.addEventListener("load", () => resolve(), {once: true});
      existingScript.addEventListener("error", () => reject(new Error("Turnstile script failed to load")), {once: true});
      return;
    }

    const script = document.createElement("script");
    script.id = TURNSTILE_SCRIPT_ID;
    script.src = TURNSTILE_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.addEventListener("load", () => resolve(), {once: true});
    script.addEventListener("error", () => reject(new Error("Turnstile script failed to load")), {once: true});
    document.head.appendChild(script);
  });

  return turnstileScriptPromise;
};

const describeSiteKey = (key: string): string => {
  const prefix = key.slice(0, 6);
  const suffix = key.slice(-4);
  return `${prefix}...${suffix} (${key.length} chars)`;
};

export const preloadTurnstileScript = (): void => {
  if (!siteKey) {
    console.error("[Turnstile] Missing VITE_TURNSTILE_SITE_KEY.");
    return;
  }

  console.info("[Turnstile] Preloading automatic script", {
    siteKey: describeSiteKey(siteKey),
    script: TURNSTILE_SCRIPT_SRC
  });

  void loadTurnstileScript().catch((error) => {
    console.error("[Turnstile] Script preload failed", error);
  });
};

const TurnstileWidget = () => {
  let responseInput!: HTMLInputElement;
  const widgetId = `aphinity-turnstile-${++turnstileWidgetSequence}`;
  const [errorMessage, setErrorMessage] = createSignal<string | null>(null);

  onMount(() => {
    if (!siteKey) {
      console.error("[Turnstile] Missing VITE_TURNSTILE_SITE_KEY.");
      setErrorMessage("Captcha is unavailable. Please contact support.");
      return;
    }

    ensureTurnstileGlobalCallbacks();
    turnstileCallbacks.set(widgetId, {
      token: (token) => {
        responseInput.value = token;
        setErrorMessage(null);
        console.info("[Turnstile] Client verification token received.");
      },
      error: (errorCode) => {
        responseInput.value = "";
        const detail = errorCode ? ` (${errorCode})` : "";
        console.error("[Turnstile] Widget error", {errorCode});
        setErrorMessage(`Captcha failed to load${detail}. Refresh and try again.`);
        toast.error("Turnstile failed to load. Refresh and try again");
      },
      clear: () => {
        responseInput.value = "";
      }
    });

    console.info("[Turnstile] Rendering automatic widget", {
      siteKey: describeSiteKey(siteKey),
      script: TURNSTILE_SCRIPT_SRC
    });

    void loadTurnstileScript()
      .catch((error) => {
        console.error("[Turnstile] Script initialization failed", error);
        setErrorMessage("Captcha failed to load. Refresh and try again.");
        toast.error("Turnstile failed to load. Refresh and try again");
      });
  });

  onCleanup(() => {
    turnstileCallbacks.delete(widgetId);
  });

  return (
    <div class="w-full min-w-0 space-y-2">
      <input ref={responseInput} type="hidden" name={TURNSTILE_RESPONSE_FIELD} value="" />
      <div
        class="cf-turnstile min-h-[65px] w-full min-w-0"
        data-aphinity-turnstile-id={widgetId}
        data-sitekey={siteKey}
        data-theme="auto"
        data-size="flexible"
        data-callback="aphinityTurnstileCallback"
        data-error-callback="aphinityTurnstileErrorCallback"
        data-expired-callback="aphinityTurnstileExpiredCallback"
        data-timeout-callback="aphinityTurnstileTimeoutCallback"
      />
      {errorMessage() && (
        <p class="text-xs text-error" role="alert">
          {errorMessage()}
        </p>
      )}
    </div>
  );
};

export default TurnstileWidget;
