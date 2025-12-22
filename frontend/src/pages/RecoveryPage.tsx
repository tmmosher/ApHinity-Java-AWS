import { A } from "@solidjs/router";
import { AuthCard } from "../components/AuthCard";

export const RecoveryPage = () => (
  <AuthCard
    title="Email Recovery"
    footer={
      <div class="space-y-2">
        <p>
          Remembered it? <A class="link link-primary" href="/login">Back to login.</A>
        </p>
        <p>
          New member? <A class="link link-primary" href="/signup">Sign up here!</A>
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
      <button type="submit" class="btn btn-primary w-full text-center">
        Submit
      </button>
    </form>
  </AuthCard>
);
