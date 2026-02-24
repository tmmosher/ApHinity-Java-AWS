import {useLocation, useNavigate} from "@solidjs/router";
import {Show, createEffect, type ParentProps} from "solid-js";
import SidebarNav from "../../components/SidebarNav";
import {useProfile} from "../../context/ProfileContext";
import {dashboardNavForAccount, isDashboardPathAllowed} from "./dashboardConfig";

export const Dashboard = (props: ParentProps) => {
  const profileContext = useProfile();
  const location = useLocation();
  const navigate = useNavigate();

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
      <div class="w-full max-w-6xl mx-auto">
        <div class="grid grid-cols-1 md:grid-cols-[220px_minmax(0,1fr)] gap-4 items-start">
          <aside class="card bg-base-100 shadow-md p-4 h-fit" aria-label="Dashboard navigation">
            <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">
              Panels
            </p>
            <Show when={profileContext.profile()} fallback={<p class="mt-2 text-sm text-base-content/60">Loading...</p>}>
              {(profile) => <SidebarNav items={dashboardNavForAccount(profile().role, profile().verified)}/>}
            </Show>
          </aside>
          <section class="card bg-base-100 shadow-md p-6 min-w-0" aria-label="Dashboard panel">
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
