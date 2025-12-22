import { A } from "@solidjs/router";
import { AuthCard } from "../components/AuthCard";

export const SignupPage = () => (
  <AuthCard
    title="Sign Up"
    footer={
      <div class="space-y-2">
        <p>
          Already a member? <A class="link link-primary" href="/login">Login here!</A>
        </p>
        <p>
          Need help? <A class="link link-primary" href="/recovery">Recover your email here!</A>
        </p>
      </div>
    }
  >
    <form class="w-full flex flex-col gap-4 text-left">
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Full name</span>
        </div>
        <input
          type="text"
          placeholder="Alex Morgan"
          class="input input-bordered w-full"
        />
      </label>
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Work email</span>
        </div>
        <input
          type="email"
          placeholder="alex@company.com"
          class="input input-bordered w-full"
        />
      </label>
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Password</span>
        </div>
        <input
          type="password"
          placeholder="Create a password"
          class="input input-bordered w-full"
        />
      </label>
      <button type="submit" class="btn btn-primary w-full text-center">
        Submit
      </button>
    </form>
  </AuthCard>
);
