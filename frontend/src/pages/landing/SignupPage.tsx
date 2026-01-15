import { A } from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";
import { createSignal, Match, Show, Switch } from "solid-js";
import ClientSignup from "./ClientSignup";
import AphinitySignup from "./AphinitySignup";

export const SignupPage = () => {
  const [chosen, setChosen] = createSignal(false);
  const [signupChoice, setSignupChoice] = createSignal("");

  return (
    <main class="w-full" aria-label="Sign up page">
      <AuthCard
        title="Sign Up"
        footer={
          <div class="space-y-2 text-left">
            <p>
              Need help? <A class="link link-primary" href="/support">Contact support here!</A>
            </p>
          </div>
        }
      >
        <Show when={!chosen()}>
          <div class="w-full flex flex-col gap-2 text-left">
            <p class="text-sm text-base-content/70">Are you a client or an ApHinity partner?</p>
            <button
              class="btn btn-primary w-full text-center"
              onClick={() => {
                setChosen(true);
                setSignupChoice("client");
              }}
            >
              Client
            </button>
            <button
              class="btn btn-primary w-full text-center"
              onClick={() => {
                setChosen(true);
                setSignupChoice("partner");
              }}
            >
              Partner
            </button>
          </div>
        </Show>
        <Show when={chosen()}>
          <div class="w-full flex flex-col gap-2 text-left">
            <p class="text-sm text-base-content/70">As a {signupChoice()}, we need....</p>
            <Switch fallback={<p class="text-sm text-base-content/70">Loading...</p>}>
              <Match when={signupChoice() === "client"}>
                <ClientSignup />
              </Match>
              <Match when={signupChoice() === "partner"}>
                <AphinitySignup />
              </Match>
            </Switch>
            <button class="btn btn-outline w-full text-center" onClick={() => setChosen(false)}>
              Back
            </button>
          </div>
        </Show>
      </AuthCard>
    </main>
  );
};
