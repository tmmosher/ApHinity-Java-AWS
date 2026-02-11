import type { JSX } from "solid-js";
import SidebarNav, {NavItem} from "../../components/SidebarNav";
import Profile from "../../components/Profile";

export type UserRole = "admin" | "partner" | "client";

type DashboardShellProps = {
  role: UserRole;
  sidebarItems: NavItem[];
  children: JSX.Element;
};

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

// TODO: Replace with role resolution
const resolveRole = (): UserRole => {
  if (typeof window === "undefined") {
    return "client";
  }

  return "client";
};

const roleNavItems: Record<UserRole, NavItem[]> = {
  admin: [
    { label: "Overview", href: "/dashboard" },
    { label: "User management" },
    { label: "Compliance queue" },
    { label: "Reporting" }
  ],
  partner: [
    { label: "Overview", href: "/dashboard" },
    { label: "Active projects" },
    { label: "Submissions" },
    { label: "Reports" }
  ],
  client: [
    { label: "Overview", href: "/dashboard" },
    { label: "Facilities" },
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
      <Profile />
    </DashboardShell>
  );
};
