import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";
import SidebarNav from "../components/SidebarNav";

const routerState = vi.hoisted(() => ({
  pathname: "/dashboard"
}));

vi.mock("@solidjs/router", () => ({
  A: (props: {
    href?: string;
    class?: string;
    children?: unknown;
    "aria-current"?: string;
    "data-dashboard-nav-link"?: string;
  }) => (
    <a
      aria-current={props["aria-current"]}
      class={props.class}
      data-dashboard-nav-link={props["data-dashboard-nav-link"]}
      href={props.href}
    >
      {props.children}
    </a>
  ),
  useLocation: () => routerState
}));

describe("SidebarNav", () => {
  it("keeps the locations entry active for location detail routes", () => {
    routerState.pathname = "/dashboard/locations/42/service-schedule";

    const html = renderToString(() => (
      <SidebarNav
        items={[
          {label: "Home", href: "/dashboard"},
          {label: "Locations", href: "/dashboard/locations"},
          {label: "Profile", href: "/dashboard/profile"}
        ]}
      />
    ));

    expect(html).toMatch(/data-dashboard-nav-item="\/dashboard\/locations"[^>]*data-active="true"/);
    expect(html).toContain('data-dashboard-nav-link="/dashboard/locations"');
    expect(html).toContain('aria-current="page"');
    expect(html).toMatch(/data-dashboard-nav-item="\/dashboard"[^>]*data-active="false"/);
    expect(html).toContain('data-focus-mode="none"');
    expect(html).not.toContain('data-focus-mode="animate-once"');
  });

  it("renders disabled items without dashboard link wrappers", () => {
    routerState.pathname = "/dashboard";

    const html = renderToString(() => (
      <SidebarNav
        items={[
          {label: "Home", href: "/dashboard"},
          {label: "Coming Soon"}
        ]}
      />
    ));

    expect(html).toContain("Coming Soon");
    expect(html).toContain('aria-disabled="true"');
    expect(html).not.toContain('data-dashboard-nav-item=""');
  });
});
