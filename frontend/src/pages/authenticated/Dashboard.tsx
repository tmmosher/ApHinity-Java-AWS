import {useLocation, useNavigate} from "@solidjs/router";
import {Show, createEffect, createSignal, type ParentProps} from "solid-js";
import SidebarNav from "../../components/common/SidebarNav";
import {useProfile} from "../../context/ProfileContext";
import {dashboardNavForAccount, isDashboardPathAllowed} from "./dashboardConfig";

export const Dashboard = (props: ParentProps) => {
  const profileContext = useProfile();
  const location = useLocation();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = createSignal(false);

  createEffect(() => {
    if (profileContext.isLoading()) {
      return;
    }

    const profile = profileContext.profile();
    if (!profile) {
      return;
    }

    if (!isDashboardPathAllowed(profile.role, location.pathname, profile.verified)) {
      void navigate("/dashboard", {
        replace: true
      });
    }
  });

  const shouldRenderPanel = () => {
    if (profileContext.isLoading()) {
      return false;
    }
    const profile = profileContext.profile();
    if (!profile) {
      return false;
    }
    return isDashboardPathAllowed(profile.role, location.pathname, profile.verified);
  };

  return (
    <main class="w-full" aria-label="Authenticated dashboard">
      <div class="mx-auto w-full max-w-[100rem] px-3 sm:px-4 lg:px-6">
        <div class="relative min-w-0">
          <button
            type="button"
            class="btn btn-square btn-sm fixed left-4 top-4 z-50 border border-base-300 bg-base-100 shadow-md"
            aria-label="Open dashboard navigation"
            aria-expanded={sidebarOpen()}
            onClick={() => setSidebarOpen(true)}
          >
            <svg
              aria-hidden="true"
              class="h-5 w-5"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              viewBox="0 0 24 24"
            >
              <path d="M4 6h16" />
              <path d="M4 12h16" />
              <path d="M4 18h16" />
            </svg>
          </button>

          <Show when={sidebarOpen()}>
            <button
              type="button"
              class="fixed inset-0 z-40 bg-base-300/40 backdrop-blur-[1px]"
              aria-label="Close dashboard navigation overlay"
              onClick={() => setSidebarOpen(false)}
            />
          </Show>

          <aside
            class={
              "fixed left-4 top-16 z-50 w-[min(18rem,calc(100vw-2rem))] rounded-2xl border border-base-300 bg-base-100 p-4 shadow-xl transition duration-200 " +
              (sidebarOpen() ? "translate-x-0 opacity-100" : "pointer-events-none -translate-x-6 opacity-0")
            }
            aria-label="Dashboard navigation"
            aria-hidden={!sidebarOpen()}
          >
            <div class="flex items-center justify-between gap-3">
              <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">
                Panels
              </p>
              <button
                type="button"
                class="btn btn-ghost btn-square btn-xs"
                aria-label="Close dashboard navigation"
                onClick={() => setSidebarOpen(false)}
              >
                <svg
                  aria-hidden="true"
                  class="h-4 w-4"
                  fill="none"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  viewBox="0 0 24 24"
                >
                  <path d="M18 6 6 18" />
                  <path d="m6 6 12 12" />
                </svg>
              </button>
            </div>
            <Show when={profileContext.profile()} fallback={<p class="mt-2 text-sm text-base-content/60">Loading...</p>}>
              {(profile) => (
                <div onClick={() => setSidebarOpen(false)}>
                  <SidebarNav items={dashboardNavForAccount(profile().role, profile().verified)}/>
                </div>
              )}
            </Show>
          </aside>

          <section class="min-w-0 rounded-2xl border border-base-300 bg-base-100 p-4 shadow-md sm:p-6" aria-label="Dashboard panel">
            <Show when={shouldRenderPanel()} fallback={
              <p class="text-base-content/70">
                {profileContext.isLoading() ? "Loading dashboard..." : "Redirecting..."}
              </p>
            }>
              {props.children}
            </Show>
          </section>
        </div>
      </div>
    </main>
  );
};
