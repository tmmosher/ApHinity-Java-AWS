import { A } from "@solidjs/router";
import { AuthCard } from "../../components/AuthCard";

export const ErrorPage = () => (
  <main class="w-full" aria-label="Error page">
    <AuthCard title="Page Not Found">
      <div class="w-full text-left space-y-4">
        <p class="text-sm text-base-content/80">
          The page you requested could not be found.
        </p>
        <div class="w-full flex flex-col gap-2">
          <A
            class="btn btn-primary w-full text-center"
            href="/"
            preload
            aria-label="Go to main page"
          >
            Main Page
          </A>
          <A
            class="btn btn-outline w-full text-center"
            href="/login"
            preload
            aria-label="Go to login page"
          >
            Login
          </A>
        </div>
      </div>
    </AuthCard>
  </main>
);