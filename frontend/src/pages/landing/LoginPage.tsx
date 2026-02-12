import { A, action, useNavigate, useSubmission } from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";
import { ActionResult } from "../../types/Types";
import {toast} from "solid-toast";
import {createEffect, createSignal, Show} from "solid-js";
import TurnstileWidget from "../../components/TurnstileWidget";
import {useApiHost} from "../../context/ApiHostContext";
import { FieldError, parseLoginFormData } from "../../util/landingSchemas";

export const LoginPage = () => {
  const host = useApiHost();
  const [failCount, setFailCount] = createSignal(0);

  const submitLogin = action(async (formData: FormData) => {
    const actionResult: ActionResult = {
      ok: false
    };
    try {
      const payload = parseLoginFormData(formData, failCount() > 3);
      const response = await fetch(host + "/api/auth/login", {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });
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
      if (error instanceof FieldError) {
        actionResult.code = "validation_failed";
        actionResult.message = error.message;
        return actionResult;
      }
      actionResult.message = "Login failed";
    }
    return actionResult;
  }, "submitLogin");

  const submission = useSubmission(submitLogin);
  const navigate = useNavigate();

  createEffect(() => {
    const result = submission.result;
    if (!result) return;
    if (result.ok) {
      setFailCount(0);
      toast.success("Logged in successfully!");
      navigate("/dashboard");
    } else {
      if (result.code !== "validation_failed") {
        setFailCount(failCount() + 1);
      }
      toast.error(result.message ?? "Login failed");
    }
    submission.clear();
    return;
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
        <Show when={failCount() > 3}>
          <TurnstileWidget/>
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
