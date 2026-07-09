import {createSignal, onCleanup, onMount} from "solid-js";
import {toast} from "solid-toast";

const TURNSTILE_SCRIPT_ID = "aphinity-turnstile-script";
const TURNSTILE_SCRIPT_SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
const TURNSTILE_RESPONSE_FIELD = "cf-turnstile-response";
const siteKey = (import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined)?.trim();

let turnstileScriptPromise: Promise<void> | null = null;

type TurnstileApi = {
  render: (
    container: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      "error-callback": (errorCode?: string) => void;
      "expired-callback": () => void;
      "timeout-callback": () => void;
      theme: "auto";
      size: "flexible" | "compact";
    }
  ) => string;
  remove: (widgetId: string) => void;
  reset: (widgetId: string) => void;
};

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

const loadTurnstileScript = (): Promise<void> => {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Turnstile requires a browser environment"));
  }
  if (window.turnstile) {
    return Promise.resolve();
  }
  if (turnstileScriptPromise) {
    return turnstileScriptPromise;
  }

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

  console.info("[Turnstile] Preloading explicit script", {
    siteKey: describeSiteKey(siteKey),
    script: TURNSTILE_SCRIPT_SRC
  });

  void loadTurnstileScript().catch((error) => {
    console.error("[Turnstile] Script preload failed", error);
  });
};

const TurnstileWidget = () => {
  let container!: HTMLDivElement;
  let responseInput!: HTMLInputElement;
  let widgetId: string | null = null;
  let renderFrame: number | null = null;
  let disposed = false;
  const [errorMessage, setErrorMessage] = createSignal<string | null>(null);

  onMount(() => {
    if (!siteKey) {
      console.error("[Turnstile] Missing VITE_TURNSTILE_SITE_KEY.");
      setErrorMessage("Captcha is unavailable. Please contact support.");
      return;
    }

    console.info("[Turnstile] Rendering explicit widget", {
      siteKey: describeSiteKey(siteKey),
      script: TURNSTILE_SCRIPT_SRC
    });

    void loadTurnstileScript()
      .then(() => {
        if (disposed) {
          return;
        }
        if (!window.turnstile) {
          throw new Error("Turnstile API unavailable after script load");
        }

        renderFrame = window.requestAnimationFrame(() => {
          if (disposed || !window.turnstile) {
            return;
          }

          const containerWidth = container.getBoundingClientRect().width;
          const size = containerWidth > 0 && containerWidth < 300 ? "compact" : "flexible";
          console.info("[Turnstile] Container measured before render", {
            width: containerWidth,
            size
          });

          widgetId = window.turnstile.render(container, {
            sitekey: siteKey,
            theme: "auto",
            size,
            callback: (token) => {
              responseInput.value = token;
              setErrorMessage(null);
              console.info("[Turnstile] Client verification token received.");
            },
            "error-callback": (errorCode) => {
              responseInput.value = "";
              const detail = errorCode ? ` (${errorCode})` : "";
              console.error("[Turnstile] Widget error", {errorCode});
              setErrorMessage(`Captcha failed to load${detail}. Refresh and try again.`);
              toast.error("Turnstile failed to load. Refresh and try again");
            },
            "expired-callback": () => {
              responseInput.value = "";
              if (widgetId && window.turnstile) {
                window.turnstile.reset(widgetId);
              }
            },
            "timeout-callback": () => {
              responseInput.value = "";
            }
          });
        });
      })
      .catch((error) => {
        console.error("[Turnstile] Script initialization failed", error);
        setErrorMessage("Captcha failed to load. Refresh and try again.");
        toast.error("Turnstile failed to load. Refresh and try again");
      });
  });

  onCleanup(() => {
    disposed = true;
    if (renderFrame != null) {
      window.cancelAnimationFrame(renderFrame);
    }
    if (widgetId && window.turnstile) {
      window.turnstile.remove(widgetId);
    }
  });

  return (
    <div class="w-full min-w-0 space-y-2">
      <input ref={responseInput} type="hidden" name={TURNSTILE_RESPONSE_FIELD} value="" />
      <div ref={container} class="min-h-[65px] w-full min-w-0" />
      {errorMessage() && (
        <p class="text-xs text-error" role="alert">
          {errorMessage()}
        </p>
      )}
    </div>
  );
};

export default TurnstileWidget;
