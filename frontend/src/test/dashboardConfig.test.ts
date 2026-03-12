import {describe, expect, it} from "vitest";
import {dashboardNavForAccount, isDashboardPathAllowed} from "../pages/authenticated/dashboardConfig";

describe("dashboardConfig", () => {
  it("allows location paths for verified users with role access", () => {
    expect(isDashboardPathAllowed("admin", "/dashboard/locations", true)).toBe(true);
    expect(isDashboardPathAllowed("client", "/dashboard/locations/42", true)).toBe(true);
  });

  it("blocks location-scoped paths for unverified users", () => {
    expect(isDashboardPathAllowed("admin", "/dashboard/locations", false)).toBe(false);
    expect(isDashboardPathAllowed("partner", "/dashboard/invite-users", false)).toBe(false);
    expect(isDashboardPathAllowed("client", "/dashboard/invites", false)).toBe(false);
  });

  it("keeps non-location dashboard paths available when unverified", () => {
    expect(isDashboardPathAllowed("client", "/dashboard", false)).toBe(true);
    expect(isDashboardPathAllowed("client", "/dashboard/profile", false)).toBe(true);
    expect(isDashboardPathAllowed("admin", "/dashboard/management", false)).toBe(true);
  });

  it("restricts the management path to admins", () => {
    expect(isDashboardPathAllowed("admin", "/dashboard/management", true)).toBe(true);
    expect(isDashboardPathAllowed("partner", "/dashboard/management", true)).toBe(false);
    expect(isDashboardPathAllowed("client", "/dashboard/management", true)).toBe(false);
  });

  it("removes location-scoped nav entries for unverified users", () => {
    const navItems = dashboardNavForAccount("partner", false);
    const hrefs = navItems.map((item) => item.href);

    expect(hrefs).toContain("/dashboard");
    expect(hrefs).toContain("/dashboard/profile");
    expect(hrefs).not.toContain("/dashboard/locations");
    expect(hrefs).not.toContain("/dashboard/invite-users");
    expect(hrefs).not.toContain("/dashboard/permissions");
  });

  it("adds the user management entry only for admins", () => {
    const adminNav = dashboardNavForAccount("admin", true);

    expect(adminNav.map((item) => item.href)).toContain("/dashboard/management");
    expect(adminNav.find((item) => item.href === "/dashboard/management")?.label).toBe("User Management");
    expect(dashboardNavForAccount("partner", true).map((item) => item.href)).not.toContain("/dashboard/management");
    expect(dashboardNavForAccount("client", true).map((item) => item.href)).not.toContain("/dashboard/management");
  });
});
