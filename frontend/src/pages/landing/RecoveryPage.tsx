import { AuthCard } from "../../components/AuthCard";
import {useApiHost} from "../../context/ApiHostContext";
import {action, useNavigate, useSubmission} from "@solidjs/router";
import {ActionResult} from "../../types/Types";
import TurnstileWidget from "../../components/TurnstileWidget";
import {createEffect} from "solid-js";
import {toast} from "solid-toast";

const host = useApiHost();

export const RecoveryPage = () => {
  const submitRecovery = action(async (formData: FormData) => {
    const actionResult: ActionResult = {
      ok: false
    };
    const response = await fetch(host + "/api/auth/recovery", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        email: formData.get("email"),
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
        actionResult.message = errorBody?.message ?? "Recovery email failed to send failed";
      }
    } catch (error) {
      actionResult.ok = false;
      actionResult.message = "Recovery email failed to send";
    }
    return actionResult;
  }, "submitRecovery");

  const submission = useSubmission(submitRecovery);
  const navigate = useNavigate();

  createEffect(() => {
    const result = submission.result;
    if (!result) return;
    if (result.ok) {
      toast.success("Recovery email sent successfully!");
      // timeout to wait for user to read the toast
      setTimeout(() => navigate("/login"), 1000);
    } else {
      toast.error(result.message ?? "Recovery email failed to send");
    }
    submission.clear();
    return;
  })

  return (
      <main class="w-full" aria-label="Email recovery page">
        <AuthCard title="Email Recovery">
          <form
              class="w-full flex flex-col gap-4 text-left"
              aria-label="Email recovery form"
              action={submitRecovery}
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
            <TurnstileWidget/>
            <button
                type="submit"
                class="btn btn-primary w-full text-center"
                aria-label="Submit email recovery form"
            >
              Submit
            </button>
          </form>
        </AuthCard>
      </main>
  );
}