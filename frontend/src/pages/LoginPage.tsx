import { A } from "@solidjs/router";
import { AuthCard } from "../components/AuthCard";

export const LoginPage = () => (
  <AuthCard
    title="Login"
    footer={
      <div class="space-y-2">
        <p>
          New member? <A class="link link-primary" href="/signup">Sign up here!</A>
        </p>
        <p>
          Forgot your email? <A class="link link-primary" href="/recovery">Recover it here!</A>
        </p>
      </div>
    }
  >
    <form class="w-full flex flex-col gap-4 text-left">
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Email</span>
        </div>
        <input
          type="email"
          placeholder="you@company.com"
          class="input input-bordered w-full"
        />
      </label>
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Password</span>
        </div>
        <input
          type="password"
          placeholder="********"
          class="input input-bordered w-full"
        />
      </label>
      <button type="submit" class="btn btn-primary w-full text-center">
        Submit
      </button>
    </form>
  </AuthCard>
);
