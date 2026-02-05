import { AuthCard } from "../../components/AuthCard";
import {useApiHost} from "../../context/ApiHostContext";
import {action, useNavigate, useSubmission} from "@solidjs/router";
import {ActionResult} from "../../types/Types";
import {createEffect, createSignal, Match, Show, Switch} from "solid-js";
import {toast} from "solid-toast";
import SendRecovery from "../../components/SendRecovery";
import SendVerification from "../../components/SendVerification";
import sendVerification from "../../components/SendVerification";

const host = useApiHost();

const RecoveryPage = () => {
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

  const submitVerification = action(async (formData: FormData) => {
        const actionResult: ActionResult = {
            ok: false
        };
        const response = await fetch(host + "/api/auth/verify", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                verifyValue: formData.get("verifyValue")
            })
        });
        try {
            if (response.ok) {
                actionResult.ok = true;
            } else {
                const errorBody = await response.json().catch(() => null);
                actionResult.ok = false;
                actionResult.code = errorBody?.code;
                actionResult.message = errorBody?.message ?? "Code verification failed";
            }
        } catch (error) {
            actionResult.ok = false;
            actionResult.message = "Code verification failed";
        }
        return actionResult;
  }, "submitVerification");

  const recoverySubmission = useSubmission(submitRecovery);
  const verificationSubmission = useSubmission(submitVerification);
  const [verifyVisible, setVerifyVisible] = createSignal(false);
  const [email, setEmail] = createSignal<string>("");
  const navigate = useNavigate();

  createEffect(() => {
    const result = recoverySubmission.result;
    if (!result) return;
    if (result.ok) {
      toast.success("Recovery email sent successfully!");
      setVerifyVisible(true);
    } else {
      toast.error(result.message ?? "Recovery email failed to send");
    }
    recoverySubmission.clear();
    return;
  });

  createEffect(() => {
    const result = verificationSubmission.result;
    if (!result) return;
    if (result.ok) {
      toast.success("Code verification successful!");
      // timeout to wait for user to read the toast
      setTimeout(() => navigate("/dashboard"), 500);
    } else {
      toast.error(result.message ?? "Recovery email failed to send");
    }
    verificationSubmission.clear();
    return;
  });

  return (
    <main class="w-full" aria-label="Email recovery page">
      <AuthCard title="Email Recovery"
        footer={
          <Show when={verifyVisible()}>
            <div>
                <p onclick={() => setVerifyVisible(false)}
                    class="text-sm text-base-content/60 cursor-pointer"
                    aria-label="Change email to send"
                >
                    Send to a different email
                </p>
            </div>
          </Show>
        }
      >
        <Switch>
          <Match when={!verifyVisible()}>
            <SendRecovery action={submitRecovery} setEmail={setEmail}/>
          </Match>
          <Match when={verifyVisible()}>
            <SendVerification action={submitVerification} email={email()}/>
          </Match>
        </Switch>
      </AuthCard>
    </main>
  );
}

export default RecoveryPage;