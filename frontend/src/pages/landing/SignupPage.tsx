import { A } from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";

export const SignupPage = () => (
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
      <form class="w-full flex flex-col gap-4 text-left" aria-label="Sign up form">
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Full name</span>
          </div>
          <input
            type="text"
            placeholder="John Doe"
            class="input opacity-70 input-bordered w-full"
            aria-label="Full name"
          />
        </label>
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Work email</span>
          </div>
          <input
            type="email"
            placeholder="john@company.com"
            class="input opacity-70 input-bordered w-full"
            aria-label="Work email"
          />
        </label>
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Password</span>
          </div>
          <input
            type="password"
            placeholder="A secure password"
            class="input opacity-70 input-bordered w-full"
            aria-label="Password"
          />
        </label>
        <button
          type="submit"
          class="btn btn-primary w-full text-center"
          aria-label="Submit sign up form"
        >
          Submit
        </button>
      </form>
    </AuthCard>
  </main>
);
