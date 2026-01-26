import {A, action, useNavigate, useSubmission} from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";
import { AuthResult } from "../../components/Types";
import {toast} from "solid-toast";
import {createEffect, createSignal, Show} from "solid-js";

declare global {
    interface Window {
        turnstile?: {
            render: (container: Element, options: Record<string, unknown>) => string;
            reset: (widgetId: string) => void;
        };
    }
}

//TODO temp before domain use
const host = "http://localhost:8080";

export const LoginPage = () => {
    const [captchaRequired, setCaptchaRequired] = createSignal(false);
    const [captchaToken, setCaptchaToken] = createSignal("");
    const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined;
    let captchaContainer: HTMLDivElement | undefined;
    let widgetId: string | undefined;

    const renderCaptcha = () => {
        if (!captchaContainer || !siteKey) {
            return;
        }
        const turnstile = window.turnstile;
        if (!turnstile) {
            setTimeout(renderCaptcha, 50);
            return;
        }
        captchaContainer.innerHTML = "";
        widgetId = turnstile.render(captchaContainer, {
            sitekey: siteKey,
            callback: (token: string) => setCaptchaToken(token),
            "expired-callback": () => setCaptchaToken(""),
            "error-callback": () => setCaptchaToken("")
        });
    };

    const submitLogin = action(async (formData: FormData) => {
    const actionResult: AuthResult = {
      ok: false
    };
    //TODO also add Zod checks for client here
    const response = await fetch(host + "/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        email: formData.get("email"),
        password: formData.get("password"),
        captchaToken: formData.get("captchaToken")
      })
    });
    try {
      if (response.ok) {
        actionResult.ok = true;
      } else {
        const errorBody = await response.json().catch(() => null);
        actionResult.ok = false;
        actionResult.code = errorBody?.code;
        actionResult.message = errorBody?.message ?? "Login failed";
      }
    } catch (error) {
      actionResult.ok = false;
      actionResult.message = "Login failed";
    }
    return actionResult;
  }, "submitLogin");

  const submission = useSubmission(submitLogin);
  const navigate = useNavigate();

  createEffect(() => {
    const result = submission.result;
    if (!result) {
      return;
    }
    if (result.ok) {
      toast.success("Logged in successfully!");
      setCaptchaRequired(false);
      setCaptchaToken("");
      navigate("/home");
      return;
    }
    if (result.code === "captcha_required" || result.code === "captcha_invalid") {
      setCaptchaRequired(true);
      renderCaptcha();
    }
    toast.error(result.message ?? "Login failed");
  });

  return(
  <main class="w-full" aria-label="Login page">
    <AuthCard
        title="Login"
        footer={
          <div class="space-y-2">
            <p>
              Forgot your password? <A class="link link-primary" href="/recovery">Recover it here!</A>
            </p>
          </div>
        }
    >
      <form class="w-full flex flex-col gap-2 text-left"
            aria-label="Login form"
            method="post"
            action={submitLogin}
      >
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Email</span>
          </div>
          <input
              type="email"
              name="email"
              placeholder="you@company.com"
              class="input opacity-70 input-bordered w-full"
              aria-label="Email"
          />
        </label>
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Password</span>
          </div>
          <input
              name="password"
              type="password"
              placeholder="********"
              class="input opacity-70 input-bordered w-full"
              aria-label="Password"
          />
        </label>
        <input type="hidden" name="captchaToken" value={captchaToken()} />
        <Show when={captchaRequired()}>
          <div class="space-y-2">
            <div ref={el => (captchaContainer = el)} />
            <Show when={!siteKey}>
              <p class="text-xs text-error">CAPTCHA site key is not configured.</p>
            </Show>
          </div>
        </Show>
        <button
            type="submit"
            class="btn btn-primary w-full text-center"
            aria-label="Submit login form"
        >
          Submit
        </button>
      </form>
    </AuthCard>
  </main>
)};
