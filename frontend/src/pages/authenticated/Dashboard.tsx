import { A } from "@solidjs/router";

export const Dashboard = () => (
  <main class="w-full" aria-label="Authenticated home page">
    <div class="w-full max-w-5xl mx-auto">
      <div class="card bg-base-100 shadow-md w-full">
        <div class="card-body p-0">
          <div class="grid grid-cols-1 md:grid-cols-[220px_1fr]">
            <aside class="border-b md:border-b-0 md:border-r border-base-200 p-4" aria-label="Sidebar">
              <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">
                Routes
              </p>
              <ul class="menu mt-2">
                <li>
                  <A class="active" href="/home" aria-current="page">
                    Home
                  </A>
                </li>
              </ul>
            </aside>
            <section class="p-6" aria-labelledby="auth-home-title">
              <h1 id="auth-home-title" class="text-2xl md:text-3xl font-bold">
                Account Home
              </h1>
              <p class="mt-2 text-base-content/70">
                This is your authenticated homepage. More routes will appear in the sidebar as they go live.
              </p>
              <div class="mt-6 rounded-box bg-base-200 p-4">
                <p class="text-sm font-semibold">Status</p>
                <p class="text-sm text-base-content/70">
                  Your account is ready. Check back for profile and organization settings.
                </p>
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  </main>
);
