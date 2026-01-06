import { AuthCard } from "../components/AuthCard";

export const RecoveryPage = () => (
  <main class="w-full" aria-label="Email recovery page">
    <AuthCard title="Email Recovery">
      <form class="w-full flex flex-col gap-4 text-left" aria-label="Email recovery form">
        <label class="form-control w-full">
          <div class="label">
            <span class="label-text">Email</span>
          </div>
          <input
            type="email"
            placeholder="you@company.com"
            class="input opacity-70 input-bordered w-full"
            aria-label="Email"
          />
        </label>
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
