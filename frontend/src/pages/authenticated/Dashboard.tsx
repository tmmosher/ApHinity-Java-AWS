import { A } from "@solidjs/router";
import { For, Match, Switch } from "solid-js";
import type { JSX } from "solid-js";

export type UserRole = "admin" | "partner" | "client";
export type NavItem = {
  label: string;
  href?: string;
};

type DashboardShellProps = {
  role: UserRole;
  sidebarItems: NavItem[];
  children: JSX.Element;
};

const SidebarNav = (props: { items: NavItem[] }) => (
  <ul class="menu mt-2">
    <For each={props.items}>
      {(item) => (
        <li>
          {item.href ? (
            <A href={item.href} activeClass="active" end preload>
              {item.label}
            </A>
          ) : (
            <span class="opacity-70 cursor-not-allowed" aria-disabled="true">
              {item.label}
            </span>
          )}
        </li>
      )}
    </For>
  </ul>
);

export const DashboardShell = (props: DashboardShellProps) => (
  <main class="w-full" aria-label="Authenticated home page">
    <div class="w-full max-w-5xl mx-auto">
      <div class="card bg-base-100 shadow-md w-full">
        <div class="card-body p-0">
          <div class="grid grid-cols-1 md:grid-cols-[220px_1fr]">
            <aside class="border-b md:border-b-0 md:border-r border-base-200 p-4" aria-label="Sidebar">
              <p class="text-xs font-semibold uppercase tracking-wide text-base-content/60">
                Routes
              </p>
              <SidebarNav items={props.sidebarItems} />
            </aside>
            <section class="p-6" aria-labelledby="auth-home-title">
              {props.children}
            </section>
          </div>
        </div>
      </div>
    </div>
  </main>
);

// TODO: Replace with role resolution from the auth context.
const resolveRole = (): UserRole => {
  if (typeof window === "undefined") {
    return "client";
  }

  return "client";
};

const roleNavItems: Record<UserRole, NavItem[]> = {
  admin: [
    { label: "Overview", href: "/home" },
    { label: "User management" },
    { label: "Compliance queue" },
    { label: "Reporting" }
  ],
  partner: [
    { label: "Overview", href: "/home" },
    { label: "Active projects" },
    { label: "Submissions" },
    { label: "Reports" }
  ],
  client: [
    { label: "Overview", href: "/home" },
    { label: "Facilities" },
    { label: "Documents" },
    { label: "Support" }
  ]
};

export const Dashboard = () => {
  const role = resolveRole();
  return (
    <DashboardShell
      role={role}
      sidebarItems={roleNavItems[role]}
    >
      <div class="mt-8 space-y-4">
        <Switch>
          <Match when={role === "admin"}>
            <div class="space-y-3">
              <h2 class="text-lg font-semibold">Admin panel</h2>
                {/*TODO*/}
            </div>
          </Match>
          <Match when={role === "partner"}>
            <div class="space-y-3">
              <h2 class="text-lg font-semibold">Partner priorities</h2>
              <ul class="list-disc list-inside text-sm text-base-content/70">
                {/*TODO*/}
              </ul>
            </div>
          </Match>
          <Match when={role === "client"}>
            <div class="space-y-3">
              <h2 class="text-lg font-semibold">Client</h2>
              <ul class="list-disc list-inside text-sm text-base-content/70">
                <li>Review facility performance indicators at a glance.</li>
                <li>Request support or schedule a consultation.</li>
              </ul>
            </div>
          </Match>
        </Switch>
      </div>
    </DashboardShell>
  );
};
