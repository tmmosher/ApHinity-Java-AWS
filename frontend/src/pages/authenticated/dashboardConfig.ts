import {NavItem} from "../../components/SidebarNav";
import {AccountRole} from "../../types/Types";

export const dashboardNavByRole: Record<AccountRole, NavItem[]> = {
  admin: [
    {label: "Home", href: "/dashboard"},
    {label: "Locations", href: "/dashboard/locations"},
    {label: "Invite users", href: "/dashboard/invite-users"},
    {label: "Permissions", href: "/dashboard/permissions"},
    {label: "Management", href: "/dashboard/management"},
    {label: "Profile", href: "/dashboard/profile"}
  ],
  partner: [
    {label: "Home", href: "/dashboard"},
    {label: "Locations", href: "/dashboard/locations"},
    {label: "Invite users", href: "/dashboard/invite-users"},
    {label: "Permissions", href: "/dashboard/permissions"},
    {label: "Profile", href: "/dashboard/profile"}
  ],
  client: [
    {label: "Home", href: "/dashboard"},
    {label: "Locations", href: "/dashboard/locations"},
    {label: "Invites", href: "/dashboard/invites"},
    {label: "Profile", href: "/dashboard/profile"}
  ]
};

const allowedPathsByRole: Record<AccountRole, string[]> = {
  admin: [
    "/dashboard",
    "/dashboard/locations",
    "/dashboard/invite-users",
    "/dashboard/permissions",
    "/dashboard/management",
    "/dashboard/profile"
  ],
  partner: [
    "/dashboard",
    "/dashboard/locations",
    "/dashboard/invite-users",
    "/dashboard/permissions",
    "/dashboard/profile"
  ],
  client: [
    "/dashboard",
    "/dashboard/locations",
    "/dashboard/invites",
    "/dashboard/profile"
  ]
};

const normalizePathname = (pathname: string): string => {
  if (pathname.startsWith("/dashboard/locations/")) {
    return "/dashboard/locations";
  }
  if (pathname.endsWith("/")) {
    return pathname.slice(0, -1);
  }
  return pathname;
};

export const isDashboardPathAllowed = (role: AccountRole, pathname: string): boolean =>
  allowedPathsByRole[role].includes(normalizePathname(pathname));
