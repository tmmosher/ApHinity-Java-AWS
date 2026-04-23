import {renderToString} from "solid-js/web";
import {describe, expect, it, vi} from "vitest";

vi.mock("@solidjs/router", () => ({
  A: (props: {children?: unknown; href: string}) => <a href={props.href}>{props.children}</a>
}));

vi.mock("corvu/popover", () => {
  const Popover = (props: {children: unknown}) => (
    <>
      {typeof props.children === "function"
        ? props.children({open: false, setOpen: vi.fn()})
        : props.children}
    </>
  );
  Popover.Trigger = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children}</button>;
  };
  Popover.Portal = (props: {children: unknown}) => <>{props.children}</>;
  Popover.Content = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <div {...rest}>{children}</div>;
  };
  Popover.Label = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  Popover.Description = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <p {...rest}>{children}</p>;
  };
  Popover.Close = (props: Record<string, unknown>) => {
    const {children, ...rest} = props;
    return <button {...rest}>{children}</button>;
  };
  return {default: Popover};
});

vi.mock("../context/ApiHostContext", () => ({
  useApiHost: () => "https://example.test"
}));

vi.mock("../context/ProfileContext", () => ({
  useProfile: () => ({
    profile: () => ({
      role: "partner",
      verified: true
    })
  })
}));

const locations = Object.assign(
  () => ([
    {
      id: 1,
      name: "Main Plant",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
      sectionLayout: {sections: []}
    },
    {
      id: 2,
      name: "Secondary Plant",
      createdAt: "2026-01-03T00:00:00Z",
      updatedAt: "2026-01-04T00:00:00Z",
      sectionLayout: {sections: []}
    }
  ]),
  {
    loading: false,
    error: undefined
  }
);

vi.mock("../context/LocationContext", () => ({
  useLocations: () => ({
    locations,
    mutate: vi.fn(),
    refetch: vi.fn()
  })
}));

vi.mock("../util/common/favoriteLocation", () => ({
  getFavoriteLocationId: () => "2",
  hasSelectableFavoriteLocation: () => true,
  setFavoriteLocationId: vi.fn()
}));

vi.mock("../util/common/recentLocation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../util/common/recentLocation")>();
  return {
    ...actual,
    getRecentLocationIds: () => ["1"]
  };
});

import {DashboardHomePanel} from "../pages/authenticated/panels/DashboardHomePanel";

describe("DashboardHomePanel", () => {
  it("renders the home page as a quick-access location card deck", () => {
    const html = renderToString(DashboardHomePanel);

    expect(html).toContain("Quick access");
    expect(html).toContain("Secondary Plant");
    expect(html).toContain("Main Plant");
    expect(html.indexOf("Secondary Plant")).toBeLessThan(html.indexOf("Main Plant"));
    expect(html).toContain("/dashboard/locations/2");
    expect(html).toContain("aria-label=\"Rename location\"");
    expect(html).toContain("aria-pressed=\"true\"");
  });
});
